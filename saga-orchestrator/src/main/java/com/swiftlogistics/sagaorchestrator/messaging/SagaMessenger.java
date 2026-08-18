package com.swiftlogistics.sagaorchestrator.messaging;

import com.swiftlogistics.sagaorchestrator.domain.SagaInstance;
import com.swiftlogistics.sagaorchestrator.domain.SagaStep;
import com.swiftlogistics.sagaorchestrator.messaging.command.StepCommand;
import com.swiftlogistics.sagaorchestrator.messaging.event.OrderStatusChangedEvent;
import java.time.Instant;
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

    /** Tells the Order Service (and anyone else listening) where the order stands. */
    public void announceOrderStatus(Long orderId, String status, String detail) {
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(orderId, status, detail, Instant.now());
        rabbitTemplate.convertAndSend(
                MessagingConstants.EVENTS_EXCHANGE,
                MessagingConstants.ORDER_STATUS_CHANGED_KEY,
                event);
        log.info("Order {}: announced status {}", orderId, status);
    }

    private StepCommand buildCommand(SagaInstance saga, SagaStep step) {
        return new StepCommand(
                saga.getOrderId(),
                step.getType().name(),
                saga.getClientId(),
                saga.getRecipientName(),
                saga.getDeliveryAddress(),
                saga.getPackageDescription());
    }
}
