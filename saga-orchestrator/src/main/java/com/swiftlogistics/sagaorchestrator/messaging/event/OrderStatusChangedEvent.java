package com.swiftlogistics.sagaorchestrator.messaging.event;

import java.time.Instant;

/**
 * Our announcement that an order has moved on. The Order Service stores it.
 *
 * sagaStep names the step the saga is on at this moment, e.g. BILLING, or the
 * step that broke when the status is COMPENSATING or FAILED. The Order Service
 * cannot read our database, so this is the only way the admin dashboard can
 * show which legacy system an order is sitting in.
 */
public record OrderStatusChangedEvent(
        Long orderId,
        String status,
        String detail,
        String sagaStep,
        Instant occurredAt) {
}
