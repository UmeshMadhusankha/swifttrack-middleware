package com.swiftlogistics.sagaorchestrator.messaging.command;

/**
 * An instruction sent to one adapter.
 *
 * All three adapters receive the same shape and each picks out the fields it
 * needs: CMS wants the client, WMS wants the order id, ROS wants the address.
 * A single flat record keeps the orchestrator free of per-system special cases.
 *
 * @param step the SagaStepType name; the adapter echoes it back in its reply
 */
public record StepCommand(
        Long orderId,
        String step,
        String clientId,
        String recipientName,
        String deliveryAddress,
        String packageDescription) {
}
