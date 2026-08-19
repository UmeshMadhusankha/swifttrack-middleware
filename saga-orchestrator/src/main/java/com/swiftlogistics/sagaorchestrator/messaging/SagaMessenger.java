package com.swiftlogistics.sagaorchestrator.messaging;

import com.swiftlogistics.sagaorchestrator.domain.SagaInstance;
import com.swiftlogistics.sagaorchestrator.domain.SagaStep;
import com.swiftlogistics.sagaorchestrator.domain.StepStatus;
import com.swiftlogistics.sagaorchestrator.messaging.command.StepCommand;
import com.swiftlogistics.sagaorchestrator.messaging.event.OrderStatusChangedEvent;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Everything the orchestrator sends out.
 *
 * Having one class own all outbound messages keeps the orchestration logic
 * readable: it decides what should happen, this decides how to say it.
 */
@Component
public class SagaMessenger {

    private static final Logger log = LoggerFactory.getLogger(SagaMessenger.class);

    private final RabbitTemplate rabbitTemplate;

    public SagaMessenger(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** Asks the step's adapter to do the work. */
    public void sendStepCommand(SagaInstance saga, SagaStep step) {
        String routingKey = step.getType().commandRoutingKey();
        rabbitTemplate.convertAndSend(MessagingConstants.COMMANDS_EXCHANGE, routingKey, buildCommand(saga, step));
        log.info("Order {}: sent command {}", saga.getOrderId(), routingKey);
    }

    /** Asks the step's adapter to undo work it already did. */
    public void sendCompensationCommand(SagaInstance saga, SagaStep step) {
        String routingKey = step.getType().compensationRoutingKey();
        rabbitTemplate.convertAndSend(MessagingConstants.COMMANDS_EXCHANGE, routingKey, buildCommand(saga, step));
        log.warn("Order {}: sent compensation {}", saga.getOrderId(), routingKey);
    }

    /**
     * Tells the Order Service (and anyone else listening) where the order stands.
     *
     * The step name is read off the saga rather than passed in, so it can never
     * drift out of step with what was just written to the saga row.
     */
    public void announceOrderStatus(SagaInstance saga, String status, String detail) {
        String step = currentStepName(saga);

        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                saga.getOrderId(), status, detail, step, Instant.now());

        rabbitTemplate.convertAndSend(
                MessagingConstants.EVENTS_EXCHANGE,
                MessagingConstants.ORDER_STATUS_CHANGED_KEY,
                event);
        log.info("Order {}: announced status {} at step {}", saga.getOrderId(), status, step);
    }

    /**
     * Which step best describes the saga right now.
     *
     * A step that has broken wins over everything else: once CMS has failed,
     * "CMS" is the useful answer for the rest of the order's life, including
     * while it is being unwound and after it has finally failed.
     */
    private String currentStepName(SagaInstance saga) {
        return firstStepWith(saga, StepStatus.FAILED)
                .or(() -> firstStepWith(saga, StepStatus.COMPENSATION_FAILED))
                .or(() -> firstStepWith(saga, StepStatus.IN_PROGRESS))
                .or(() -> firstStepWith(saga, StepStatus.COMPENSATING))
                .or(saga::nextPendingStep)
                .map(step -> step.getType().name())
                .orElse("COMPLETED");
    }

    private Optional<SagaStep> firstStepWith(SagaInstance saga, StepStatus status) {
        return saga.getSteps().stream()
                .filter(step -> step.getStatus() == status)
                .findFirst();
    }

    private StepCommand buildCommand(SagaInstance saga, SagaStep step) {
        return new StepCommand(
                saga.getOrderId(),
                step.getType().name(),
                saga.getClientId(),
                saga.getRecipientName(),
                saga.getDeliveryAddress(),
                saga.getPackageDescription(),
                step.getExternalReference());
    }
}
