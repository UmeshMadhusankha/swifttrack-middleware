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
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Every new order starts life as PENDING, so there is only one way to build one. */
    public static Order placeNew(String clientId, String recipientName, String deliveryAddress,
                                 String packageDescription) {
        return new Order(clientId, recipientName, deliveryAddress, packageDescription);
    }

    public void changeStatus(OrderStatus newStatus, String detail) {
        this.status = newStatus;
        this.statusDetail = detail;
        this.updatedAt = Instant.now();
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
