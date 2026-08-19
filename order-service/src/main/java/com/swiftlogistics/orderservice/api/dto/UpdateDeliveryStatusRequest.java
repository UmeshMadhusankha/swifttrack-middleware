package com.swiftlogistics.orderservice.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of PATCH /api/orders/{id}/delivery-status.
 *
 * The reason is optional because it only means anything for a failure, and is
 * validated against the status in the service rather than here.
 */
public record UpdateDeliveryStatusRequest(
        @NotBlank(message = "status is required")
        String status,

        String reason) {
}
