package com.swiftlogistics.sagaorchestrator.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The orchestrator's memory of one order's journey through CMS, WMS and ROS.
 *
 * Everything needed to continue or to roll back lives here, including a copy of
 * the order details, so the orchestrator never has to call the Order Service
 * back and can resume correctly even after a restart.
 *
 * The step objects are only mutated through the methods on this class. That way
 * the saga row and its steps can never drift out of agreement.
 */
@Entity
@Table(name = "saga_instance")
public class SagaInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One saga per order. The unique constraint makes duplicate events harmless. */
    @Column(nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaState state;

    /** Why the saga failed, carried through to the client-facing status. */
    private String failureReason;

    // A snapshot of the order, used to build the commands sent to the adapters.
    private String clientId;
    private String recipientName;
    private String deliveryAddress;
    private String packageDescription;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<SagaStep> steps = new ArrayList<>();

    protected SagaInstance() {
        // Required by JPA.
    }

    private SagaInstance(Long orderId, String clientId, String recipientName, String deliveryAddress,
                         String packageDescription) {
        this.orderId = orderId;
        this.clientId = clientId;
        this.recipientName = recipientName;
        this.deliveryAddress = deliveryAddress;
        this.packageDescription = packageDescription;
        this.state = SagaState.RUNNING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;

        for (SagaStepType type : SagaStepType.values()) {
            this.steps.add(new SagaStep(this, type));
        }
    }

    /** Starts a saga with one PENDING step per entry in {@link SagaStepType}. */
    public static SagaInstance start(Long orderId, String clientId, String recipientName,
                                     String deliveryAddress, String packageDescription) {
        return new SagaInstance(orderId, clientId, recipientName, deliveryAddress, packageDescription);
    }

    // ---------------------------------------------------------------------
    // Deciding what to do next
    // ---------------------------------------------------------------------

    /** The next step to run on the happy path, or empty when all of them are done. */
    public Optional<SagaStep> nextPendingStep() {
        return steps.stream()
                .filter(step -> step.getStatus() == StepStatus.PENDING)
                .min(Comparator.comparingInt(SagaStep::getSequence));
    }

    /**
     * The next step to undo, or empty when there is nothing left to undo.
     *
     * Compensation runs backwards: the most recently completed step is the
     * first one reversed, exactly like unwinding a stack.
     */
    public Optional<SagaStep> nextStepToCompensate() {
        return steps.stream()
                .filter(step -> step.getStatus() == StepStatus.COMPLETED)
                .max(Comparator.comparingInt(SagaStep::getSequence));
    }

    public Optional<SagaStep> findStep(SagaStepType type) {
        return steps.stream()
                .filter(step -> step.getType() == type)
                .findFirst();
    }

    /** Steps whose adapter has stayed silent past the given cutoff. */
    public List<SagaStep> stepsAwaitingReplySince(Instant cutoff) {
        return steps.stream()
                .filter(step -> step.hasTimedOut(cutoff))
                .toList();
    }

    // ---------------------------------------------------------------------
    // Recording what happened
    // ---------------------------------------------------------------------

    public void markStepInProgress(SagaStep step) {
        step.markInProgress();
        touch();
    }

    public void recordStepSuccess(SagaStep step, String detail, String externalReference) {
        step.markCompleted(detail, externalReference);
        touch();
    }

    /** A step failed, so the saga stops going forward and starts unwinding. */
    public void recordStepFailure(SagaStep step, String reason) {
        step.markFailed(reason);
        this.state = SagaState.COMPENSATING;
        this.failureReason = reason;
        touch();
    }

    public void markStepCompensating(SagaStep step) {
        step.markCompensating();
        touch();
    }

    public void recordCompensationSuccess(SagaStep step, String detail) {
        step.markCompensated(detail);
        touch();
    }

    /**
     * An undo failed. We keep unwinding the remaining steps regardless, because
     * undoing two of three is better than undoing none, but the saga ends in
     * FAILED so it is visible that a legacy system was left inconsistent.
     */
    public void recordCompensationFailure(SagaStep step, String reason) {
        step.markCompensationFailed(reason);
        touch();
    }

    public void markCompleted() {
        this.state = SagaState.COMPLETED;
        touch();
    }

    /** Called once every completed step has been reversed. */
    public void markCompensationFinished() {
        boolean anyUndoFailed = steps.stream()
                .anyMatch(step -> step.getStatus() == StepStatus.COMPENSATION_FAILED);

        this.state = anyUndoFailed ? SagaState.FAILED : SagaState.COMPENSATED;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ---------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public SagaState getState() {
        return state;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getPackageDescription() {
        return packageDescription;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<SagaStep> getSteps() {
        return List.copyOf(steps);
    }
}
