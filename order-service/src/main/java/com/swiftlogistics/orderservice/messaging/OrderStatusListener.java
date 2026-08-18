package com.swiftlogistics.orderservice.messaging;

import com.swiftlogistics.orderservice.messaging.event.OrderStatusChangedEvent;
import com.swiftlogistics.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Applies status updates announced by the SAGA orchestrator.
 *
 * This is what makes the frontend's polling endpoint show live progress: the
 * orchestrator drives the legacy systems, and every move it makes lands here
 * and is written back onto the order row.
 */
@Component
public class OrderStatusListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusListener.class);

    private final OrderService orderService;

    public OrderStatusListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = MessagingConstants.ORDER_STATUS_QUEUE)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.debug("Received status update {} for order {}", event.status(), event.orderId());
        orderService.applyStatusUpdate(event.orderId(), event.status(), event.detail());
    }
}
