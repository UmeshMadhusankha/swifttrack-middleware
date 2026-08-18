package com.swiftlogistics.cmsadapter.messaging.command;

/**
 * An instruction from the SAGA orchestrator.
 *
 * On the way forward externalReference is null. On the way back it holds the
 * invoice number CMS gave us, so the cancellation knows what to void.
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
