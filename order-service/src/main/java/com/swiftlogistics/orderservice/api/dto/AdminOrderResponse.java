package com.swiftlogistics.orderservice.api.dto;

import com.swiftlogistics.orderservice.domain.Order;
import com.swiftlogistics.orderservice.domain.SagaProgress;
import java.time.Instant;

/**
 * One row of the admin dashboard's live pipeline table.
 *
 * Richer than {@link OrderResponse} because the admin view shows the internals
 * a client has no business seeing: which saga step is running and how each of
 * the three legacy systems replied. Kept as its own record so widening the
 * admin view can never widen what a client's own order listing returns.
 */
public record AdminOrderResponse(
        Long id,
        String clientId,
        String recipientName,
        String deliveryAddress,
        String packageDescription,
        String status,
        String statusDetail,
        String sagaStep,
        String cmsStatus,
        String wmsStatus,
        String rosStatus,
        String deliveryStatus,
        String deliveryStatusReason,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminOrderResponse from(Order order) {
        SagaProgress progress = order.sagaProgress();

        return new AdminOrderResponse(
                order.getId(),
                order.getClientId(),
                order.getRecipientName(),
                order.getDeliveryAddress(),
                order.getPackageDescription(),
                order.getStatus().name(),
                order.getStatusDetail(),
                progress.currentStep(),
                progress.cms(),
                progress.wms(),
                progress.ros(),
                order.getDeliveryStatus().name(),
                order.getDeliveryStatusReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
