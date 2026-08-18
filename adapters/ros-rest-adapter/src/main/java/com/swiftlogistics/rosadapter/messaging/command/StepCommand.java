package com.swiftlogistics.rosadapter.messaging.command;

/**
 * An instruction from the SAGA orchestrator.
 *
 * On the way forward externalReference is null. On the way back it holds the
 * route id ROS gave us, which is the only way to cancel the right route.
 */
public record StepCommand(
        Long orderId,
        String step,
        String clientId,
        String recipientName,
        String deliveryAddress,
        String packageDescription,
        String externalReference) {
}
