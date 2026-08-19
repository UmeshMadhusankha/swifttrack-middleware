package com.swiftlogistics.orderservice.service;

import com.swiftlogistics.orderservice.domain.DeliveryStatus;
import com.swiftlogistics.orderservice.domain.Order;
import com.swiftlogistics.orderservice.domain.OrderStatus;
import com.swiftlogistics.orderservice.messaging.OrderEventPublisher;
import com.swiftlogistics.orderservice.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All business logic for orders. The controller only translates HTTP to these calls. */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Saves the order as PENDING and announces it.
     *
     * The save is committed before we publish, so the orchestrator can never
     * receive an event for an order that is not in the database yet.
     */
    public Order placeOrder(String clientId, String recipientName, String deliveryAddress,
                            String packageDescription) {
        Order order = orderRepository.save(
                Order.placeNew(clientId, recipientName, deliveryAddress, packageDescription));

        eventPublisher.publishOrderCreated(order);
        return order;
    }

    /**
     * Records a status change announced by the orchestrator.
     *
     * Updates are ignored when the order is already finished or when the status
     * name is one we do not recognise, so a bad or late message cannot corrupt
     * an order that has already reached its final state.
     */
    @Transactional
    public void applyStatusUpdate(Long orderId, String statusName, String detail, String sagaStep) {
        Optional<Order> found = orderRepository.findById(orderId);
        if (found.isEmpty()) {
            log.warn("Status update for unknown order {}, ignoring", orderId);
            return;
        }

        Order order = found.get();
        if (order.getStatus().isTerminal()) {
            log.warn("Order {} is already {}, ignoring update to {}", orderId, order.getStatus(), statusName);
            return;
        }

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(statusName);
        } catch (IllegalArgumentException ex) {
            log.error("Unknown status '{}' for order {}, ignoring", statusName, orderId);
            return;
        }

        order.changeStatus(newStatus, detail, sagaStep);
        log.info("Order {} is now {} (saga step {})", orderId, newStatus, sagaStep);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Every order in the system, newest first. Admin use only.
     *
     * The caller's role is checked at the controller, which is the only place
     * that can see it. Nothing here decides who is allowed to call it, so this
     * method must never be reachable from a client-facing endpoint.
     */
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * The orders a driver can act on: those the middleware has fully processed.
     *
     * Anything short of COMPLETED is still moving through CMS, WMS or ROS, or
     * has failed outright. Handing those to a driver would send someone out
     * with a parcel the warehouse has not reserved.
     */
    @Transactional(readOnly = true)
    public List<Order> findReadyForDelivery() {
        return orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.COMPLETED);
    }

    /**
     * Records what happened at the door.
     *
     * Written straight to the order row. There is no saga and no RabbitMQ
     * message involved: this is the physical delivery, which happens after the
     * middleware has finished, and no legacy system needs undoing if it fails.
     *
     * @throws IllegalArgumentException if the status is not a delivery status
     * @throws IllegalStateException    if the middleware has not finished with the order
     */
    @Transactional
    public Optional<Order> recordDeliveryOutcome(Long orderId, String statusName, String reason) {
        DeliveryStatus newStatus;
        try {
            newStatus = DeliveryStatus.valueOf(statusName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown delivery status '" + statusName + "'");
        }

        if (newStatus == DeliveryStatus.PENDING_DELIVERY) {
            throw new IllegalArgumentException(
                    "A delivery can only be marked DELIVERED or DELIVERY_FAILED");
        }

        Optional<Order> found = orderRepository.findById(orderId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Order order = found.get();
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Order " + orderId + " is " + order.getStatus()
                            + " and has not finished processing yet");
        }

        order.recordDeliveryOutcome(newStatus, reason);
        log.info("Order {} delivery marked {}{}", orderId, newStatus,
                reason == null || reason.isBlank() ? "" : " (" + reason + ")");
        return Optional.of(order);
    }

    /**
     * Every order belonging to one client.
     *
     * Separate from {@link #findAll()} rather than being the same method with a
     * nullable argument. One method that quietly returns everything when its
     * argument happens to be null is the kind of thing a forgetful caller turns
     * into a data leak.
     */
    @Transactional(readOnly = true)
    public List<Order> findForClient(String clientId) {
        return orderRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }
}
