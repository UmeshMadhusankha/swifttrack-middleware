package com.swiftlogistics.orderservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A delivery order submitted by a SwiftLogistics client.
 *
 * The table is named "orders" because ORDER is a reserved SQL keyword.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String deliveryAddress;

    private String packageDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** Human-readable note about the current status, e.g. why it failed. */
    private String statusDetail;

    /**
     * Which saga step the orchestrator was on when it last announced a status,
     * e.g. BILLING. Null for orders created before the orchestrator started
     * sending it. Only ever copied from an event; never decided here.
     */
    private String sagaStep;

    /**
     * Set by a driver once the middleware has finished with the order.
     *
     * Nullable in the database on purpose: the column is added to a table that
     * may already hold rows, and Postgres will not accept a NOT NULL column
     * without a default on a populated table. Rows predating it read as
     * PENDING_DELIVERY, which is what they were.
     */
    @Enumerated(EnumType.STRING)
    private DeliveryStatus deliveryStatus;

    /** Why a delivery failed, as chosen by the driver. Null unless it failed. */
    private String deliveryStatusReason;

    /** When a driver last touched the delivery status. Null until they do. */
    private Instant deliveryUpdatedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Order() {
        // Required by JPA.
    }

    private Order(String clientId, String recipientName, String deliveryAddress, String packageDescription) {
        this.clientId = clientId;
        this.recipientName = recipientName;
        this.deliveryAddress = deliveryAddress;
        this.packageDescription = packageDescription;
        this.status = OrderStatus.PENDING;
        this.deliveryStatus = DeliveryStatus.PENDING_DELIVERY;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Every new order starts life as PENDING, so there is only one way to build one. */
    public static Order placeNew(String clientId, String recipientName, String deliveryAddress,
                                 String packageDescription) {
        return new Order(clientId, recipientName, deliveryAddress, packageDescription);
    }

    public void changeStatus(OrderStatus newStatus, String detail, String sagaStep) {
        this.status = newStatus;
        this.statusDetail = detail;
        if (sagaStep != null && !sagaStep.isBlank()) {
            this.sagaStep = sagaStep;
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Records what the driver found at the door.
     *
     * The reason is kept only for a failure. Carrying a stale "recipient not
     * available" alongside a DELIVERED order would be actively misleading to
     * whoever reads the row later.
     */
    public void recordDeliveryOutcome(DeliveryStatus newStatus, String reason) {
        this.deliveryStatus = newStatus;
        this.deliveryStatusReason = newStatus == DeliveryStatus.DELIVERY_FAILED ? reason : null;
        this.deliveryUpdatedAt = Instant.now();
        this.updatedAt = this.deliveryUpdatedAt;
    }

    /** How far through CMS, WMS and ROS this order has got. */
    public SagaProgress sagaProgress() {
        return SagaProgress.of(status, sagaStep);
    }

    public Long getId() {
        return id;
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

    public OrderStatus getStatus() {
        return status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public String getSagaStep() {
        return sagaStep;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus == null ? DeliveryStatus.PENDING_DELIVERY : deliveryStatus;
    }

    public String getDeliveryStatusReason() {
        return deliveryStatusReason;
    }

    public Instant getDeliveryUpdatedAt() {
        return deliveryUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
