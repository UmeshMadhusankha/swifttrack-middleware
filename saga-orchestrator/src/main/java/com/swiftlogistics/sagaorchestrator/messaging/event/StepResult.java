package com.swiftlogistics.sagaorchestrator.messaging.event;

/**
 * An adapter's reply to a command.
 *
 * The same shape covers both directions: a reply to "do the work" arrives on
 * saga.step.*, and a reply to "undo the work" arrives on saga.compensation.*.
 *
 * @param step the SagaStepType name, echoed back so we know which step replied
 * @param detail a human-readable note, or the error message when it failed
 * @param externalReference the handle the legacy system gave us for the work it
 *                          just did, such as a route id or an invoice number.
 *                          Null when the system has nothing to hand back.
 */
public record StepResult(
        Long orderId,
        String step,
        boolean success,
        String detail,
        String externalReference) {
}
