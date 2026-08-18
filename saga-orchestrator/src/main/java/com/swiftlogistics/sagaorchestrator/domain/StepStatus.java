package com.swiftlogistics.sagaorchestrator.domain;

/** Where a single saga step currently stands. */
public enum StepStatus {

    /** Not started yet. */
    PENDING,

    /** Command sent to the adapter, waiting for its reply. */
    IN_PROGRESS,

    /** The adapter reported success. This is the state that later needs undoing. */
    COMPLETED,

    /** The adapter reported failure, or never replied in time. */
    FAILED,

    /** An undo command has been sent, waiting for confirmation. */
    COMPENSATING,

    /** The work was successfully undone. */
    COMPENSATED,

    /** The undo itself failed. Needs a human; we cannot fix this automatically. */
    COMPENSATION_FAILED
}
