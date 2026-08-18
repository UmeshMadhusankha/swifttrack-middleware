package com.swiftlogistics.orderservice.messaging;

import com.swiftlogistics.orderservice.domain.Order;
import com.swiftlogistics.orderservice.messaging.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Turns a saved Order into an "order.created" announcement on the events exchange. */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getClientId(),
                order.getRecipientName(),
                order.getDeliveryAddress(),
                order.getPackageDescription(),
                order.getCreatedAt());

        rabbitTemplate.convertAndSend(
                MessagingConstants.EVENTS_EXCHANGE,
                MessagingConstants.ORDER_CREATED_KEY,
                event);

        log.info("Published order.created for order {}", order.getId());
    }
}
