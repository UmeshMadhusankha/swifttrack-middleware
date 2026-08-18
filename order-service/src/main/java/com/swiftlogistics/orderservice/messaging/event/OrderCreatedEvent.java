package com.swiftlogistics.orderservice.messaging.event;

import java.time.Instant;

/**
 * Announced when a new order has been saved as PENDING.
 *
 * It carries everything the SAGA orchestrator needs to talk to CMS, WMS and
 * ROS, so the orchestrator never has to call back into this service.
 */
public record OrderCreatedEvent(
        Long orderId,
        String clientId,
        String recipientName,
        String deliveryAddress,
        String packageDescription,
        Instant createdAt) {
}
