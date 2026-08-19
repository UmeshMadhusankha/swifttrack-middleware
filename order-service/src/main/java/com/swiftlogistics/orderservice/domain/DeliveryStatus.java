package com.swiftlogistics.orderservice.domain;

/**
 * What has happened to the parcel in the physical world, after the middleware
 * has finished with it.
 *
 * Deliberately separate from {@link OrderStatus}. That one tracks the saga
 * through CMS, WMS and ROS and is written only by the orchestrator's events;
 * this one is written only by a driver, and only once the saga has COMPLETED.
 * Keeping them apart means a driver marking a delivery failed can never be
 * mistaken for the middleware failing to process the order.
 */
public enum DeliveryStatus {

    /** The middleware is done; nobody has been out to the address yet. */
    PENDING_DELIVERY,

    /** The driver handed the parcel over. */
    DELIVERED,

    /** The driver could not complete the delivery. See the reason on the order. */
    DELIVERY_FAILED
}
