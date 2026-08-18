package com.swiftlogistics.sagaorchestrator.messaging;

/**
 * The RabbitMQ topology of the middleware, from the orchestrator's point of view.
 *
 * Two exchanges are used on purpose:
 *   - commands are instructions aimed at one specific adapter ("bill this order")
 *   - events are announcements anyone may listen to ("this order got billed")
 * Keeping them apart makes it obvious which messages expect someone to act.
 */
public final class MessagingConstants {

    public static final String COMMANDS_EXCHANGE = "swifttrack.commands";
    public static final String EVENTS_EXCHANGE = "swifttrack.events";

    // --- What we listen to -------------------------------------------------

    /** New orders from the Order Service. */
    public static final String ORDER_CREATED_QUEUE = "saga.order-created.q";
    public static final String ORDER_CREATED_KEY = "order.created";

    /** Adapter replies to forward-progress commands. */
    public static final String STEP_RESULT_QUEUE = "saga.step-results.q";
    public static final String STEP_RESULT_PATTERN = "saga.step.*";

    /** Adapter replies to undo commands. */
    public static final String COMPENSATION_RESULT_QUEUE = "saga.compensation-results.q";
    public static final String COMPENSATION_RESULT_PATTERN = "saga.compensation.*";

    // --- What we announce --------------------------------------------------

    /** Consumed by the Order Service to keep the client-facing status current. */
    public static final String ORDER_STATUS_CHANGED_KEY = "order.status.changed";

    private MessagingConstants() {
        // Constants holder, never instantiated.
    }
}
