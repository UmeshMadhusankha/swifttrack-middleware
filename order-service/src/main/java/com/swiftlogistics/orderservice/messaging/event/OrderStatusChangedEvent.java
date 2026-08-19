package com.swiftlogistics.orderservice.messaging.event;

import java.time.Instant;

/**
 * Sent by the SAGA orchestrator whenever an order changes state.
 *
 * The status is a plain String rather than our OrderStatus enum on purpose:
 * the orchestrator is a separate service and must not depend on our classes.
 * We translate it to the enum on arrival and ignore anything we do not know.
 *
 * sagaStep names the orchestrator's step at the moment of the announcement,
 * e.g. BILLING. It is what lets the admin dashboard show which of the three
 * legacy systems an order is sitting in without this service being able to
 * read the orchestrator's database. Null on messages from an older build.
 */
public record OrderStatusChangedEvent(
        Long orderId,
        String status,
        String detail,
        String sagaStep,
        Instant occurredAt) {
}
