package com.swiftlogistics.orderservice.api.dto;

import com.swiftlogistics.orderservice.domain.Order;
import java.time.Instant;

/**
 * What the frontend sees.
 *
 * Kept separate from the Order entity so that changing the database schema
 * does not silently change the shape of the public API.
 */
public record OrderResponse(
        Long id,
        String clientId,
        String recipientName,
        String deliveryAddress,
        String packageDescription,
        String status,
        String statusDetail,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getClientId(),
                order.getRecipientName(),
                order.getDeliveryAddress(),
                order.getPackageDescription(),
                order.getStatus().name(),
                order.getStatusDetail(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
