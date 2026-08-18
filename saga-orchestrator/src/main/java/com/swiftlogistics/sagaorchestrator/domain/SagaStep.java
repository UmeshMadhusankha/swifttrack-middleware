package com.swiftlogistics.sagaorchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** One step of one saga: "did CMS bill order 42 yet, and is it still billed?" */
@Entity
@Table(name = "saga_step")
public class SagaStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saga_id", nullable = false)
    private SagaInstance saga;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStepType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status;

    /** Copied from the step type so the database can sort steps in workflow order. */
    @Column(name = "step_sequence", nullable = false)
    private int sequence;

    /** Whatever the adapter told us, e.g. a confirmation note or an error message. */
    private String detail;

    /**
     * The handle the legacy system gave us for the work it did.
     *
     * This is the piece that makes compensation possible. ROS returns a route
     * id when it plans a route, and the only way to cancel that route later is
     * to send the id back. If we used it and threw it away, the undo would have
     * nothing to point at.
     */
    private String externalReference;

    /** When the outstanding command was sent. Used to detect adapters that never reply. */
    private Instant awaitingReplySince;

    protected SagaStep() {
        // Required by JPA.
    }

    SagaStep(SagaInstance saga, SagaStepType type) {
        this.saga = saga;
        this.type = type;
        this.sequence = type.sequence();
        this.status = StepStatus.PENDING;
    }

    void markInProgress() {
        this.status = StepStatus.IN_PROGRESS;
        this.awaitingReplySince = Instant.now();
    }

    void markCompleted(String detail, String externalReference) {
        this.status = StepStatus.COMPLETED;
        this.detail = detail;
        this.externalReference = externalReference;
        this.awaitingReplySince = null;
    }

    void markFailed(String detail) {
        this.status = StepStatus.FAILED;
        this.detail = detail;
        this.awaitingReplySince = null;
    }

    void markCompensating() {
        this.status = StepStatus.COMPENSATING;
        this.awaitingReplySince = Instant.now();
    }

    void markCompensated(String detail) {
        this.status = StepStatus.COMPENSATED;
        this.detail = detail;
        this.awaitingReplySince = null;
    }

    void markCompensationFailed(String detail) {
        this.status = StepStatus.COMPENSATION_FAILED;
        this.detail = detail;
        this.awaitingReplySince = null;
    }

    /** True when we sent a command and the adapter has been silent for too long. */
    public boolean hasTimedOut(Instant cutoff) {
        boolean waitingForReply = status == StepStatus.IN_PROGRESS || status == StepStatus.COMPENSATING;
        return waitingForReply && awaitingReplySince != null && awaitingReplySince.isBefore(cutoff);
    }

    public Long getId() {
        return id;
    }

    public SagaInstance getSaga() {
        return saga;
    }

    public SagaStepType getType() {
        return type;
    }

    public StepStatus getStatus() {
        return status;
    }

    public int getSequence() {
        return sequence;
    }

    public String getDetail() {
        return detail;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Instant getAwaitingReplySince() {
        return awaitingReplySince;
    }
}
