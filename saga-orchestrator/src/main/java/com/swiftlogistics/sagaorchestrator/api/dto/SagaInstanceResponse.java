package com.swiftlogistics.sagaorchestrator.api.dto;

import com.swiftlogistics.sagaorchestrator.domain.SagaInstance;
import java.time.Instant;
import java.util.List;

/** Full saga state for the debug endpoint. */
public record SagaInstanceResponse(
        Long id,
        Long orderId,
        String state,
        String failureReason,
        String clientId,
        String recipientName,
        String deliveryAddress,
        Instant createdAt,
        Instant updatedAt,
        List<SagaStepResponse> steps) {

    public static SagaInstanceResponse from(SagaInstance saga) {
        return new SagaInstanceResponse(
                saga.getId(),
                saga.getOrderId(),
                saga.getState().name(),
                saga.getFailureReason(),
                saga.getClientId(),
                saga.getRecipientName(),
                saga.getDeliveryAddress(),
                saga.getCreatedAt(),
                saga.getUpdatedAt(),
                saga.getSteps().stream()
                        .map(SagaStepResponse::from)
                        .toList());
    }
}
