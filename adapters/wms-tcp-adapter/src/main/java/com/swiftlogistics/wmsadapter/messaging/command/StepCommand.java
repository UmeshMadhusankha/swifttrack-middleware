package com.swiftlogistics.wmsadapter.messaging.command;

/**
 * An instruction from the SAGA orchestrator.
 *
 * This adapter only needs the order id and the step name, but the record
 * mirrors the full message so that Jackson can read it without complaint and
 * so the shape is obvious when reading the code.
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
