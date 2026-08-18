package com.swiftlogistics.wmsadapter.messaging;

import com.swiftlogistics.wmsadapter.messaging.command.StepCommand;
import com.swiftlogistics.wmsadapter.messaging.event.StepResult;
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

    public void publishSuccess(StepCommand command, CommandKind kind, String detail) {
        publish(command, kind, true, detail);
    }

    public void publishFailure(StepCommand command, CommandKind kind, String reason) {
        log.error("Order {}: {} {} failed - {}", command.orderId(), kind, command.step(), reason);
        publish(command, kind, false, reason);
    }

    private void publish(StepCommand command, CommandKind kind, boolean success, String detail) {
        // The warehouse gives us no id to remember, so externalReference is null.
        StepResult result = new StepResult(command.orderId(), command.step(), success, detail, null);

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
