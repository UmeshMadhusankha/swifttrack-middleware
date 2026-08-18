package com.swiftlogistics.cmsadapter.messaging;

import com.swiftlogistics.cmsadapter.messaging.command.StepCommand;
import com.swiftlogistics.cmsadapter.messaging.event.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Answers the orchestrator on the routing key that matches the outcome. */
@Component
public class StepResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(StepResultPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public StepResultPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** @param invoiceNumber stored by the orchestrator for a possible later cancellation */
    public void publishSuccess(StepCommand command, CommandKind kind, String detail, String invoiceNumber) {
        publish(command, kind, true, detail, invoiceNumber);
    }

    public void publishFailure(StepCommand command, CommandKind kind, String reason) {
        log.error("Order {}: {} {} failed - {}", command.orderId(), kind, command.step(), reason);
        publish(command, kind, false, reason, null);
    }

    private void publish(StepCommand command, CommandKind kind, boolean success,
                         String detail, String externalReference) {
        StepResult result =
                new StepResult(command.orderId(), command.step(), success, detail, externalReference);

        rabbitTemplate.convertAndSend(MessagingConstants.EVENTS_EXCHANGE, routingKeyFor(kind, success), result);
        log.info("Order {}: replied {} for {} {}",
                command.orderId(), success ? "success" : "failure", kind, command.step());
    }

    private String routingKeyFor(CommandKind kind, boolean success) {
        if (kind == CommandKind.FORWARD) {
            return success ? MessagingConstants.STEP_COMPLETED_KEY : MessagingConstants.STEP_FAILED_KEY;
        }
        return success
                ? MessagingConstants.COMPENSATION_COMPLETED_KEY
                : MessagingConstants.COMPENSATION_FAILED_KEY;
    }
}
