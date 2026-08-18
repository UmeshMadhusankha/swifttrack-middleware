package com.swiftlogistics.orderservice.messaging;

/**
 * Names of the RabbitMQ exchanges, queues and routing keys this service uses.
 *
 * Keeping them in one place makes it obvious what the service listens to and
 * what it announces, and stops typos in string literals scattered around.
 */
public final class MessagingConstants {

    /** Topic exchange carrying "something happened" announcements. */
    public static final String EVENTS_EXCHANGE = "swifttrack.events";

    /** Published when a client submits a new order. */
    public static final String ORDER_CREATED_KEY = "order.created";

    /** Published by the orchestrator every time an order moves forward or fails. */
    public static final String ORDER_STATUS_CHANGED_KEY = "order.status.changed";

    /** Our own queue of status updates to apply to the orders table. */
    public static final String ORDER_STATUS_QUEUE = "order.status.q";

    private MessagingConstants() {
        // Constants holder, never instantiated.
    }
}
