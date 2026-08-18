package com.swiftlogistics.wmsadapter.messaging;

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
 * Declares this adapter's two queues.
 *
 * Forward commands and undo commands get separate queues rather than one queue
 * and an if-statement. Each listener method then has exactly one job, and it is
 * impossible to confuse a reserve with a release.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange commandsExchange() {
        return new TopicExchange(MessagingConstants.COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(MessagingConstants.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue reserveQueue() {
        return QueueBuilder.durable(MessagingConstants.RESERVE_QUEUE).build();
    }

    @Bean
    public Binding reserveBinding(Queue reserveQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(reserveQueue).to(commandsExchange).with(MessagingConstants.RESERVE_KEY);
    }

    @Bean
    public Queue releaseQueue() {
        return QueueBuilder.durable(MessagingConstants.RELEASE_QUEUE).build();
    }

    @Bean
    public Binding releaseBinding(Queue releaseQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(releaseQueue).to(commandsExchange).with(MessagingConstants.RELEASE_KEY);
    }

    /** JSON message bodies, decoded into the record named on the listener method. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
