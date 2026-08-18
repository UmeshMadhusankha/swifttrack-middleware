package com.swiftlogistics.sagaorchestrator.messaging;

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
 * Declares the exchanges and the three queues the orchestrator consumes.
 *
 * The adapter queues are declared by the adapters themselves, so each service
 * owns the part of the topology it depends on.
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
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(MessagingConstants.ORDER_CREATED_QUEUE).build();
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(eventsExchange)
                .with(MessagingConstants.ORDER_CREATED_KEY);
    }

    @Bean
    public Queue stepResultQueue() {
        return QueueBuilder.durable(MessagingConstants.STEP_RESULT_QUEUE).build();
    }

    @Bean
    public Binding stepResultBinding(Queue stepResultQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(stepResultQueue)
                .to(eventsExchange)
                .with(MessagingConstants.STEP_RESULT_PATTERN);
    }

    @Bean
    public Queue compensationResultQueue() {
        return QueueBuilder.durable(MessagingConstants.COMPENSATION_RESULT_QUEUE).build();
    }

    @Bean
    public Binding compensationResultBinding(Queue compensationResultQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(compensationResultQueue)
                .to(eventsExchange)
                .with(MessagingConstants.COMPENSATION_RESULT_PATTERN);
    }

    /**
     * Sends and receives message bodies as JSON.
     *
     * The type mapper is set to INFERRED so we deserialize into the record type
     * named on our listener method instead of trusting the sender's class-name
     * header, which points at a class that does not exist in this service.
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
