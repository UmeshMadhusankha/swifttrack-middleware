package com.swiftlogistics.rosadapter.messaging.event;

/**
 * This adapter's reply to the orchestrator.
 *
 * On a successful plan, externalReference carries the route id. The
 * orchestrator writes it onto the saga step, which is what makes a later
 * cancellation possible.
 */
public record StepResult(
        Long orderId,
        String step,
        boolean success,
        String detail,
        String externalReference) {
}
