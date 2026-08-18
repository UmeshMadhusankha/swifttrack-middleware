package com.swiftlogistics.cmsadapter.messaging;

/** The slice of the middleware's RabbitMQ topology that this adapter owns. */
public final class MessagingConstants {

    public static final String COMMANDS_EXCHANGE = "swifttrack.commands";
    public static final String EVENTS_EXCHANGE = "swifttrack.events";

    /** Forward work: bill the order. */
    public static final String BILLING_QUEUE = "cms.commands.q";
    public static final String BILLING_KEY = "cms.billing.create";

    /** Undo work: void the invoice. */
    public static final String CANCEL_QUEUE = "cms.compensations.q";
    public static final String CANCEL_KEY = "cms.billing.cancel";

    public static final String STEP_COMPLETED_KEY = "saga.step.completed";
    public static final String STEP_FAILED_KEY = "saga.step.failed";
    public static final String COMPENSATION_COMPLETED_KEY = "saga.compensation.completed";
    public static final String COMPENSATION_FAILED_KEY = "saga.compensation.failed";

    private MessagingConstants() {
        // Constants holder, never instantiated.
    }
}
