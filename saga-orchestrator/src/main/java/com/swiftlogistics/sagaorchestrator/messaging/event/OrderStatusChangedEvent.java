package com.swiftlogistics.sagaorchestrator.messaging.event;

import java.time.Instant;

/** Our announcement that an order has moved on. The Order Service stores it. */
public record OrderStatusChangedEvent(
        Long orderId,
        String status,
        String detail,
        Instant occurredAt) {
}
