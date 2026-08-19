package com.swiftlogistics.sagaorchestrator.api.dto;

import com.swiftlogistics.sagaorchestrator.domain.SagaStep;
import java.time.Instant;

/** One step in the saga, as seen by the debug endpoint. */
public record SagaStepResponse(
        String type,
        int sequence,
        String status,
        String detail,
        String externalReference,
        Instant awaitingReplySince,
        Instant completedAt) {

    public static SagaStepResponse from(SagaStep step) {
        return new SagaStepResponse(
                step.getType().name(),
                step.getSequence(),
                step.getStatus().name(),
                step.getDetail(),
                step.getExternalReference(),
                step.getAwaitingReplySince(),
                step.getCompletedAt());
    }
}
