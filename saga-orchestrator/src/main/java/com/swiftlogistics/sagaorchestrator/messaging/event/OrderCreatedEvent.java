package com.swiftlogistics.sagaorchestrator.messaging.event;

import java.time.Instant;

/**
 * A new order, as announced by the Order Service.
 *
 * This is our own copy of the message shape rather than a class shared with the
 * Order Service. Sharing a library would force both services to be rebuilt and
 * redeployed together, which is exactly the coupling the middleware exists to
 * avoid. The price is keeping two small records in step.
 */
public record OrderCreatedEvent(
        Long orderId,
        String clientId,
        String recipientName,
        String deliveryAddress,
        String packageDescription,
        Instant createdAt) {
}
