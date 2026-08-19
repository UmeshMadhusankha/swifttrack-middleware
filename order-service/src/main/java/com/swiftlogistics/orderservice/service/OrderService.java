package com.swiftlogistics.orderservice.service;

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
    public void applyStatusUpdate(Long orderId, String statusName, String detail) {
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

        order.changeStatus(newStatus, detail);
        log.info("Order {} is now {}", orderId, newStatus);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Every order belonging to one client.
     *
     * There is deliberately no way to ask for "all orders". A single method
     * that returns everything when its argument happens to be null is the kind
     * of thing one forgetful caller turns into a data leak.
     */
    @Transactional(readOnly = true)
    public List<Order> findForClient(String clientId) {
        return orderRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }
}
