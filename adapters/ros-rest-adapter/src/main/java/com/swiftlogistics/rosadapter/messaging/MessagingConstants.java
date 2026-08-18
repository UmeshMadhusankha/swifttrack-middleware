package com.swiftlogistics.rosadapter.messaging;

/** The slice of the middleware's RabbitMQ topology that this adapter owns. */
public final class MessagingConstants {

    public static final String COMMANDS_EXCHANGE = "swifttrack.commands";
    public static final String EVENTS_EXCHANGE = "swifttrack.events";

    /** Forward work: plan the delivery route. */
    public static final String PLAN_QUEUE = "ros.commands.q";
    public static final String PLAN_KEY = "ros.route.plan";

    /** Undo work: cancel the planned route. */
    public static final String CANCEL_QUEUE = "ros.compensations.q";
    public static final String CANCEL_KEY = "ros.route.cancel";

    public static final String STEP_COMPLETED_KEY = "saga.step.completed";
    public static final String STEP_FAILED_KEY = "saga.step.failed";
    public static final String COMPENSATION_COMPLETED_KEY = "saga.compensation.completed";
    public static final String COMPENSATION_FAILED_KEY = "saga.compensation.failed";

    private MessagingConstants() {
        // Constants holder, never instantiated.
    }
}
