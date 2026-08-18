package com.swiftlogistics.orderservice.messaging.event;

import java.time.Instant;

/**
 * Sent by the SAGA orchestrator whenever an order changes state.
 *
 * The status is a plain String rather than our OrderStatus enum on purpose:
 * the orchestrator is a separate service and must not depend on our classes.
 * We translate it to the enum on arrival and ignore anything we do not know.
 */
public record OrderStatusChangedEvent(
        Long orderId,
        String status,
        String detail,
        Instant occurredAt) {
}
