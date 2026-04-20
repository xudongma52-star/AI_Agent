// config/RabbitMQConfig.java
package com.max.ai_agent.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RabbitMQConfig {

    // 交换机名称
    public static final String CHAT_EXCHANGE = "chat.exchange";

    // 队列名称
    public static final String CHAT_MEMORY_QUEUE = "chat.memory.queue";

    // 死信交换机
    public static final String CHAT_DEAD_EXCHANGE = "chat.dead.exchange";

    // 死信队列（人工处理/告警）
    public static final String CHAT_DEAD_QUEUE = "chat.dead.queue";

    // 路由Key
    public static final String CHAT_ROUTING_KEY = "chat.memory.save";
    public static final String DEAD_ROUTING_KEY = "chat.dead.save";

    // 正常交换机和队列

    @Bean
    public DirectExchange chatExchange() {
        // DirectExchange：精确匹配routingKey
        // durable=true：持久化，RabbitMQ重启不丢失
        return new DirectExchange(CHAT_EXCHANGE, true, false);
    }

    @Bean
    public Queue chatMemoryQueue() {
        return QueueBuilder
                .durable(CHAT_MEMORY_QUEUE)  // 持久化队列
                .withArgument("x-dead-letter-exchange", CHAT_DEAD_EXCHANGE)  // 死信交换机
                .withArgument("x-dead-letter-routing-key", DEAD_ROUTING_KEY) // 死信路由
                .withArgument("x-message-ttl", 300000) // 队列消息最长存活5分钟
                .build();
    }

    @Bean
    public Binding chatMemoryBinding() {
        // 把队列绑定到交换机，指定routingKey
        return BindingBuilder
                .bind(chatMemoryQueue())
                .to(chatExchange())
                .with(CHAT_ROUTING_KEY);
    }

    // 死信交换机和队列

    @Bean
    public DirectExchange chatDeadExchange() {
        return new DirectExchange(CHAT_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue chatDeadQueue() {
        return QueueBuilder
                .durable(CHAT_DEAD_QUEUE)
                .build();
    }

    @Bean
    public Binding deadBinding() {
        return BindingBuilder
                .bind(chatDeadQueue())
                .to(chatDeadExchange())
                .with(DEAD_ROUTING_KEY);
    }

    // JSON消息转换器

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        // 发送的Java对象自动转成JSON
        // 接收的JSON自动转成Java对象
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());

        // 消息发送到Exchange失败时回调
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送到Exchange失败，原因：{}", cause);
                // TODO: 可以记录到数据库，后续补偿
            }
        });

        // 消息从Exchange路由到Queue失败时回调
        template.setReturnsCallback(returned -> {
            log.error("消息路由到Queue失败，消息：{}", returned.getMessage());
        });

        return template;
    }
}