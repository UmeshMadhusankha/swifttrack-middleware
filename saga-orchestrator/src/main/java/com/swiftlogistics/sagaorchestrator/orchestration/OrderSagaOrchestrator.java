package com.swiftlogistics.sagaorchestrator.orchestration;

import com.swiftlogistics.sagaorchestrator.domain.SagaInstance;
import com.swiftlogistics.sagaorchestrator.domain.SagaStep;
import com.swiftlogistics.sagaorchestrator.domain.SagaStepType;
import com.swiftlogistics.sagaorchestrator.domain.StepStatus;
import com.swiftlogistics.sagaorchestrator.messaging.SagaMessenger;
import com.swiftlogistics.sagaorchestrator.messaging.event.OrderCreatedEvent;
import com.swiftlogistics.sagaorchestrator.messaging.event.StepResult;
import com.swiftlogistics.sagaorchestrator.repository.SagaInstanceRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives an order through CMS, then WMS, then ROS, and undoes the work in
 * reverse if any of them fails.
 *
 * The three legacy systems have no shared transaction: once CMS has created an
 * invoice, no database rollback can remove it. So instead of rolling back we
 * compensate, meaning we send an explicit "undo" instruction to each system
 * that already succeeded, newest first. That is the SAGA pattern.
 *
 * The orchestrator never blocks waiting for a reply. Every method here handles
 * one message and then returns; the saga row in the database is what remembers
 * where the order got to between messages.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final SagaInstanceRepository sagaRepository;
    private final SagaMessenger messenger;

    public OrderSagaOrchestrator(SagaInstanceRepository sagaRepository, SagaMessenger messenger) {
        this.sagaRepository = sagaRepository;
        this.messenger = messenger;
    }

    /**
     * Begins a saga for a newly created order and fires off the first command.
     *
     * If a saga already exists for this order the event is a duplicate and is
     * dropped. RabbitMQ can deliver the same message more than once, so every
     * entry point has to survive being called twice.
     */
    @Transactional
    public void startSaga(OrderCreatedEvent event) {
        if (sagaRepository.existsByOrderId(event.orderId())) {
            log.warn("Saga for order {} already exists, ignoring duplicate order.created", event.orderId());
            return;
        }

        SagaInstance saga = sagaRepository.save(SagaInstance.start(
                event.orderId(),
                event.clientId(),
                event.recipientName(),
                event.deliveryAddress(),
                event.packageDescription()));

        log.info("Order {}: saga started", saga.getOrderId());
        messenger.announceOrderStatus(saga.getOrderId(), "PROCESSING", "Order accepted by the middleware");

        sendNextStep(saga);
    }

    /** Handles an adapter's reply to a "do the work" command. */
    @Transactional
    public void handleStepResult(StepResult result) {
        Optional<SagaInstance> loaded = sagaRepository.findByOrderId(result.orderId());
        if (loaded.isEmpty()) {
            log.warn("Step result for unknown order {}, ignoring", result.orderId());
            return;
        }

        SagaInstance saga = loaded.get();
        Optional<SagaStep> found = resolveStep(saga, result.step());
        if (found.isEmpty()) {
            return;
        }

        SagaStep step = found.get();
        // Anything other than IN_PROGRESS means we already dealt with this step,
        // for example because it timed out just before the reply arrived.
        if (step.getStatus() != StepStatus.IN_PROGRESS) {
            log.warn("Order {}: ignoring {} result, step is already {}",
                    saga.getOrderId(), step.getType(), step.getStatus());
            return;
        }

        if (result.success()) {
            saga.recordStepSuccess(step, result.detail(), result.externalReference());
            log.info("Order {}: step {} succeeded", saga.getOrderId(), step.getType());
            messenger.announceOrderStatus(
                    saga.getOrderId(), step.getType().successOrderStatus(), result.detail());
            sendNextStep(saga);
        } else {
            beginCompensation(saga, step, result.detail());
        }
    }

    /** Handles an adapter's reply to an "undo the work" command. */
    @Transactional
    public void handleCompensationResult(StepResult result) {
        Optional<SagaInstance> loaded = sagaRepository.findByOrderId(result.orderId());
        if (loaded.isEmpty()) {
            log.warn("Compensation result for unknown order {}, ignoring", result.orderId());
            return;
        }

        SagaInstance saga = loaded.get();
        Optional<SagaStep> found = resolveStep(saga, result.step());
        if (found.isEmpty()) {
            return;
        }

        SagaStep step = found.get();
        if (step.getStatus() != StepStatus.COMPENSATING) {
            log.warn("Order {}: ignoring {} compensation result, step is already {}",
                    saga.getOrderId(), step.getType(), step.getStatus());
            return;
        }

        if (result.success()) {
            saga.recordCompensationSuccess(step, result.detail());
            log.info("Order {}: step {} undone", saga.getOrderId(), step.getType());
        } else {
            saga.recordCompensationFailure(step, result.detail());
            log.error("Order {}: could not undo step {} ({}). {} may be left inconsistent.",
                    saga.getOrderId(), step.getType(), result.detail(), step.getType());
        }

        compensateNextStep(saga);
    }

    /**
     * Treats an adapter's silence as a failure.
     *
     * Called by the timeout monitor. Without this a crashed adapter would leave
     * the order stuck mid-saga forever, with warehouse stock reserved and no
     * one coming to collect it.
     */
    @Transactional
    public void handleStepTimeout(Long orderId, SagaStepType stepType) {
        Optional<SagaInstance> loaded = sagaRepository.findByOrderId(orderId);
        if (loaded.isEmpty()) {
            return;
        }

        SagaInstance saga = loaded.get();
        Optional<SagaStep> found = saga.findStep(stepType);
        if (found.isEmpty()) {
            return;
        }

        SagaStep step = found.get();
        String reason = "No reply from the " + stepType + " adapter in time";

        // The reply may have arrived between the scan and this call, so check again.
        if (step.getStatus() == StepStatus.IN_PROGRESS) {
            log.error("Order {}: step {} timed out", orderId, stepType);
            beginCompensation(saga, step, reason);
        } else if (step.getStatus() == StepStatus.COMPENSATING) {
            log.error("Order {}: compensation for step {} timed out", orderId, stepType);
            saga.recordCompensationFailure(step, reason);
            compensateNextStep(saga);
        }
    }

    // -------------------------------------------------------------------
    // The two directions the saga can move
    // -------------------------------------------------------------------

    /**
     * Sends the command for the next step that has not run yet, or finishes the
     * saga when there are none left.
     */
    private void sendNextStep(SagaInstance saga) {
        Optional<SagaStep> next = saga.nextPendingStep();

        if (next.isEmpty()) {
            saga.markCompleted();
            log.info("Order {}: saga completed, all three systems succeeded", saga.getOrderId());
            messenger.announceOrderStatus(saga.getOrderId(), "COMPLETED", "Order is ready for delivery");
            return;
        }

        SagaStep step = next.get();
        saga.markStepInProgress(step);
        messenger.sendStepCommand(saga, step);
    }

    /** Records the failure that stopped the saga, then starts unwinding. */
    private void beginCompensation(SagaInstance saga, SagaStep failedStep, String reason) {
        saga.recordStepFailure(failedStep, reason);
        log.error("Order {}: step {} failed ({}), compensating",
                saga.getOrderId(), failedStep.getType(), reason);

        messenger.announceOrderStatus(saga.getOrderId(), "COMPENSATING",
                "Undoing completed steps: " + reason);

        compensateNextStep(saga);
    }

    /**
     * Undoes the most recently completed step, or finishes the saga when every
     * completed step has been reversed.
     *
     * Steps are undone one at a time and newest first, so the systems are
     * unwound in the opposite order to how they were called.
     */
    private void compensateNextStep(SagaInstance saga) {
        Optional<SagaStep> toUndo = saga.nextStepToCompensate();

        if (toUndo.isEmpty()) {
            saga.markCompensationFinished();
            log.warn("Order {}: saga finished as {} after compensation",
                    saga.getOrderId(), saga.getState());
            messenger.announceOrderStatus(saga.getOrderId(), "FAILED", saga.getFailureReason());
            return;
        }

        SagaStep step = toUndo.get();
        saga.markStepCompensating(step);
        messenger.sendCompensationCommand(saga, step);
    }

    /** Turns the step name from a message back into an enum, tolerating rubbish. */
    private Optional<SagaStep> resolveStep(SagaInstance saga, String stepName) {
        SagaStepType type;
        try {
            type = SagaStepType.valueOf(stepName);
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.error("Order {}: unknown step name '{}' in reply, ignoring", saga.getOrderId(), stepName);
            return Optional.empty();
        }

        Optional<SagaStep> step = saga.findStep(type);
        if (step.isEmpty()) {
            log.error("Order {}: saga has no step {}, ignoring", saga.getOrderId(), type);
        }
        return step;
    }
}
