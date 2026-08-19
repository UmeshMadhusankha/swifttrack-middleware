package com.swiftlogistics.orderservice.repository;

import com.swiftlogistics.orderservice.domain.Order;
import com.swiftlogistics.orderservice.domain.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByClientIdOrderByCreatedAtDesc(String clientId);

    List<Order> findAllByOrderByCreatedAtDesc();

    /** The driver's work list: orders the middleware has finished with. */
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
