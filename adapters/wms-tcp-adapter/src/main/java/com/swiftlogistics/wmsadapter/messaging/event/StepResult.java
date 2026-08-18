package com.swiftlogistics.wmsadapter.messaging.event;

/**
 * This adapter's reply to the orchestrator.
 *
 * The warehouse hands back nothing but an acknowledgement, so
 * externalReference is always null here. It is kept in the record because all
 * three adapters answer with the same message shape.
 */
public record StepResult(
        Long orderId,
        String step,
        boolean success,
        String detail,
        String externalReference) {
}
