package com.swiftlogistics.cmsadapter.messaging.event;

/**
 * This adapter's reply to the orchestrator.
 *
 * On a successful billing, externalReference carries the invoice number. The
 * orchestrator stores it on the saga so a later compensation can quote it back.
 */
public record StepResult(
        Long orderId,
        String step,
        boolean success,
        String detail,
        String externalReference) {
}
