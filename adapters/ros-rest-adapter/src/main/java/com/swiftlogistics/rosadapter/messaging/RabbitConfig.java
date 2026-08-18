package com.swiftlogistics.rosadapter.messaging;

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

/** Declares this adapter's planning queue and its cancellation queue. */
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
    public Queue planQueue() {
        return QueueBuilder.durable(MessagingConstants.PLAN_QUEUE).build();
    }

    @Bean
    public Binding planBinding(Queue planQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(planQueue).to(commandsExchange).with(MessagingConstants.PLAN_KEY);
    }

    @Bean
    public Queue cancelQueue() {
        return QueueBuilder.durable(MessagingConstants.CANCEL_QUEUE).build();
    }

    @Bean
    public Binding cancelBinding(Queue cancelQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(cancelQueue).to(commandsExchange).with(MessagingConstants.CANCEL_KEY);
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
