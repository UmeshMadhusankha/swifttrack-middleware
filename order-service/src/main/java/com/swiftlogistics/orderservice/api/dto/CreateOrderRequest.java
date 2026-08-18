package com.swiftlogistics.orderservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /api/orders. */
public record CreateOrderRequest(
        @NotBlank(message = "recipientName is required")
        String recipientName,

        @NotBlank(message = "deliveryAddress is required")
        String deliveryAddress,

        String packageDescription) {
}
