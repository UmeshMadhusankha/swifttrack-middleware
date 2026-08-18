package com.swiftlogistics.wmsadapter.messaging;

/** The slice of the middleware's RabbitMQ topology that this adapter owns. */
public final class MessagingConstants {

    public static final String COMMANDS_EXCHANGE = "swifttrack.commands";
    public static final String EVENTS_EXCHANGE = "swifttrack.events";

    /** Forward work: reserve the goods. */
    public static final String RESERVE_QUEUE = "wms.commands.q";
    public static final String RESERVE_KEY = "wms.stock.reserve";

    /** Undo work: put the goods back. */
    public static final String RELEASE_QUEUE = "wms.compensations.q";
    public static final String RELEASE_KEY = "wms.stock.release";

    // Replies. The routing key says how it went, so the orchestrator can bind
    // separate queues to forward results and compensation results.
    public static final String STEP_COMPLETED_KEY = "saga.step.completed";
    public static final String STEP_FAILED_KEY = "saga.step.failed";
    public static final String COMPENSATION_COMPLETED_KEY = "saga.compensation.completed";
    public static final String COMPENSATION_FAILED_KEY = "saga.compensation.failed";

    private MessagingConstants() {
        // Constants holder, never instantiated.
    }
}
