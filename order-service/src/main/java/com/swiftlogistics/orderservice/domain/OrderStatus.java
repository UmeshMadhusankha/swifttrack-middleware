package com.swiftlogistics.orderservice.domain;

/**
 * The lifecycle of an order as seen by the client.
 *
 * Only PENDING is set by this service. Every other value arrives as an
 * "order.status.changed" event from the SAGA orchestrator as it drives the
 * order through CMS, WMS and ROS.
 */
public enum OrderStatus {

    /** Saved to our database, not yet picked up by the orchestrator. */
    PENDING,

    /** The orchestrator has started the saga. */
    PROCESSING,

    /** CMS accepted the billing record. */
    BILLED,

    /** WMS reserved warehouse stock. */
    STOCK_RESERVED,

    /** ROS produced a delivery route. */
    ROUTE_PLANNED,

    /** All three legacy systems succeeded. */
    COMPLETED,

    /** A step failed and the orchestrator is undoing the earlier ones. */
    COMPENSATING,

    /** The order could not be fulfilled; any completed work has been undone. */
    FAILED;

    /** Terminal states never change again, so late updates can be ignored. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
