package com.swiftlogistics.orderservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ topology this service needs.
 *
 * Spring creates the exchange, queue and binding on startup if they do not
 * already exist, so there is no manual setup in the RabbitMQ console.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(MessagingConstants.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderStatusQueue() {
        return QueueBuilder.durable(MessagingConstants.ORDER_STATUS_QUEUE).build();
    }

    @Bean
    public Binding orderStatusBinding(Queue orderStatusQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderStatusQueue)
                .to(eventsExchange)
                .with(MessagingConstants.ORDER_STATUS_CHANGED_KEY);
    }

    /**
     * Sends and receives message bodies as JSON.
     *
     * The type mapper is set to INFERRED so we deserialize into the record type
     * named on our listener method. Without it, Spring would trust the sender's
     * class-name header, which points at a class that only exists inside the
     * orchestrator and would fail here.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
