package com.swiftlogistics.orderservice.messaging;

import com.swiftlogistics.orderservice.messaging.event.OrderStatusChangedEvent;
import com.swiftlogistics.orderservice.service.OrderService;
import com.swiftlogistics.orderservice.websocket.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Applies status updates announced by the SAGA orchestrator.
 *
 * This is what makes the frontend's WebSocket connection show live progress:
 * the orchestrator drives the legacy systems, every move it makes lands here,
 * is written back onto the order row, and immediately pushed to any browser
 * watching that order over WebSocket.
 */
@Component
public class OrderStatusListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusListener.class);

    private final OrderService orderService;
    private final WebSocketSessionManager webSocketSessionManager;

    public OrderStatusListener(OrderService orderService,
                               WebSocketSessionManager webSocketSessionManager) {
        this.orderService = orderService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @RabbitListener(queues = MessagingConstants.ORDER_STATUS_QUEUE)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.debug("Received status update {} for order {}", event.status(), event.orderId());
        orderService.applyStatusUpdate(event.orderId(), event.status(), event.detail(), event.sagaStep());
        // Push to any browser watching this order over WebSocket.
        webSocketSessionManager.pushStatusUpdate(event.orderId(), event.status(), event.detail());
    }
}
