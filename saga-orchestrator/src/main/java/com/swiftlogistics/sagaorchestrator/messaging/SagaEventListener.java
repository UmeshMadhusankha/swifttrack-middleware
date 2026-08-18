package com.swiftlogistics.sagaorchestrator.messaging;

import com.swiftlogistics.sagaorchestrator.messaging.event.OrderCreatedEvent;
import com.swiftlogistics.sagaorchestrator.messaging.event.StepResult;
import com.swiftlogistics.sagaorchestrator.orchestration.OrderSagaOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The orchestrator's inbox.
 *
 * These methods do no thinking of their own; they unwrap the message and hand
 * it to the orchestrator, which holds all the decision-making in one place.
 */
@Component
public class SagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);

    private final OrderSagaOrchestrator orchestrator;

    public SagaEventListener(OrderSagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @RabbitListener(queues = MessagingConstants.ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.debug("Received order.created for order {}", event.orderId());
        orchestrator.startSaga(event);
    }

    @RabbitListener(queues = MessagingConstants.STEP_RESULT_QUEUE)
    public void onStepResult(StepResult result) {
        log.debug("Received step result {} for order {}", result, result.orderId());
        orchestrator.handleStepResult(result);
    }

    @RabbitListener(queues = MessagingConstants.COMPENSATION_RESULT_QUEUE)
    public void onCompensationResult(StepResult result) {
        log.debug("Received compensation result {} for order {}", result, result.orderId());
        orchestrator.handleCompensationResult(result);
    }
}
