package com.swiftlogistics.sagaorchestrator.domain;

/** Where the saga as a whole currently stands. */
public enum SagaState {

    /** Working through the steps in order. */
    RUNNING,

    /** All steps succeeded. */
    COMPLETED,

    /** A step failed; undoing the steps that had already succeeded. */
    COMPENSATING,

    /** Rolled back cleanly. The order failed, but nothing was left half-done. */
    COMPENSATED,

    /** Rollback itself broke. Some legacy system is left inconsistent. */
    FAILED;

    public boolean isFinished() {
        return this == COMPLETED || this == COMPENSATED || this == FAILED;
    }
}
