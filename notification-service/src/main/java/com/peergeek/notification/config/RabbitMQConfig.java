package com.peergeek.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String DIRECT_EXCHANGE = "peergeek.tasks.direct";
    public static final String EMAIL_DISPATCH_QUEUE = "task.email.dispatch";
    public static final String EMAIL_ROUTING_KEY = "task.email.dispatch";

    public static final String DEAD_LETTER_EXCHANGE = "peergeek.dlx";
    public static final String DEAD_LETTER_QUEUE = "peergeek.tasks.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead.task.email";

    @Bean
    public DirectExchange tasksExchange() {
        return new DirectExchange(DIRECT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue emailDispatchQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        args.put("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY);
        args.put("x-message-ttl", 86400000); // 24 hours
        return new Queue(EMAIL_DISPATCH_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(DEAD_LETTER_QUEUE, true, false, false);
    }

    @Bean
    public Binding emailDispatchBinding(Queue emailDispatchQueue, DirectExchange tasksExchange) {
        return BindingBuilder.bind(emailDispatchQueue).to(tasksExchange).with(EMAIL_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}

