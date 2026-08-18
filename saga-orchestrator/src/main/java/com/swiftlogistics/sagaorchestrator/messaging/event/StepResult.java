package com.swiftlogistics.sagaorchestrator.messaging.event;

/**
 * An adapter's reply to a command.
 *
 * The same shape covers both directions: a reply to "do the work" arrives on
 * saga.step.*, and a reply to "undo the work" arrives on saga.compensation.*.
 *
 * @param step the SagaStepType name, echoed back so we know which step replied
 * @param detail an invoice number, a route id, or an error message
 */
public record StepResult(
        Long orderId,
        String step,
        boolean success,
        String detail) {
}
