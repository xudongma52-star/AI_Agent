package com.max.ai_agent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RabbitMQConfig 配置类 单元测试")
class RabbitMQConfigTest {

    @Mock
    private ConnectionFactory connectionFactory;

    private RabbitMQConfig rabbitMQConfig;

    @BeforeEach
    void setUp() {
        rabbitMQConfig = new RabbitMQConfig();
    }

    // ==========================
    // 常量测试
    // ==========================

    @Nested
    @DisplayName("常量值验证")
    class ConstantTests {

        @Test void constant_chatExchange() {
            assertEquals("chat.exchange", RabbitMQConfig.CHAT_EXCHANGE);
        }

        @Test void constant_chatMemoryQueue() {
            assertEquals("chat.memory.queue", RabbitMQConfig.CHAT_MEMORY_QUEUE);
        }

        @Test void constant_chatDeadExchange() {
            assertEquals("chat.dead.exchange", RabbitMQConfig.CHAT_DEAD_EXCHANGE);
        }

        @Test void constant_chatDeadQueue() {
            assertEquals("chat.dead.queue", RabbitMQConfig.CHAT_DEAD_QUEUE);
        }

        @Test void constant_chatRoutingKey() {
            assertEquals("chat.memory.save", RabbitMQConfig.CHAT_ROUTING_KEY);
        }

        @Test void constant_deadRoutingKey() {
            assertEquals("chat.dead.save", RabbitMQConfig.DEAD_ROUTING_KEY);
        }
    }

    // ==========================
    // Exchange 测试
    // ==========================

    @Nested
    @DisplayName("正常交换机测试")
    class ChatExchangeTests {

        @Test
        void chatExchange_shouldBeCorrect() {
            DirectExchange exchange = rabbitMQConfig.chatExchange();

            assertAll(
                    () -> assertNotNull(exchange),
                    () -> assertEquals(RabbitMQConfig.CHAT_EXCHANGE, exchange.getName()),
                    () -> assertTrue(exchange.isDurable()),
                    () -> assertFalse(exchange.isAutoDelete())
            );
        }
    }

    // ==========================
    // Queue 测试
    // ==========================

    @Nested
    @DisplayName("正常队列测试")
    class ChatMemoryQueueTests {

        private Queue queue;

        @BeforeEach
        void setup() {
            queue = rabbitMQConfig.chatMemoryQueue();
        }

        @Test
        void queue_basicProperties() {
            assertAll(
                    () -> assertEquals(RabbitMQConfig.CHAT_MEMORY_QUEUE, queue.getName()),
                    () -> assertTrue(queue.isDurable())
            );
        }

        @Test
        void queue_deadLetterConfig() {
            Map<String, Object> args = queue.getArguments();

            assertAll(
                    () -> assertNotNull(args),
                    () -> assertEquals(RabbitMQConfig.CHAT_DEAD_EXCHANGE,
                            args.get("x-dead-letter-exchange")),
                    () -> assertEquals(RabbitMQConfig.DEAD_ROUTING_KEY,
                            args.get("x-dead-letter-routing-key")),
                    () -> assertEquals(300000,
                            args.get("x-message-ttl"))
            );
        }
    }

    // ==========================
    // Binding 测试
    // ==========================

    @Nested
    @DisplayName("绑定关系测试")
    class BindingTests {

        @Test
        void chatMemoryBinding_shouldBeCorrect() {
            Binding binding = rabbitMQConfig.chatMemoryBinding();

            assertAll(
                    () -> assertEquals(RabbitMQConfig.CHAT_EXCHANGE, binding.getExchange()),
                    () -> assertEquals(RabbitMQConfig.CHAT_MEMORY_QUEUE, binding.getDestination()),
                    () -> assertEquals(RabbitMQConfig.CHAT_ROUTING_KEY, binding.getRoutingKey()),
                    () -> assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType())
            );
        }
    }

    // ==========================
    // 死信配置测试
    // ==========================

    @Nested
    @DisplayName("死信配置测试")
    class DeadLetterTests {

        @Test
        void deadExchange_shouldBeCorrect() {
            DirectExchange exchange = rabbitMQConfig.chatDeadExchange();

            assertAll(
                    () -> assertEquals(RabbitMQConfig.CHAT_DEAD_EXCHANGE, exchange.getName()),
                    () -> assertTrue(exchange.isDurable())
            );
        }

        @Test
        void deadQueue_shouldBeCorrect() {
            Queue deadQueue = rabbitMQConfig.chatDeadQueue();

            assertAll(
                    () -> assertEquals(RabbitMQConfig.CHAT_DEAD_QUEUE, deadQueue.getName()),
                    () -> assertTrue(deadQueue.isDurable())
            );

            Map<String, Object> args = deadQueue.getArguments();
            assertTrue(args == null || !args.containsKey("x-dead-letter-exchange"),
                    "死信队列不应再次配置死信交换机，避免死信循环");
        }

        @Test
        void deadBinding_shouldBeCorrect() {
            Binding binding = rabbitMQConfig.deadBinding();

            assertAll(
                    () -> assertEquals(RabbitMQConfig.CHAT_DEAD_EXCHANGE, binding.getExchange()),
                    () -> assertEquals(RabbitMQConfig.CHAT_DEAD_QUEUE, binding.getDestination()),
                    () -> assertEquals(RabbitMQConfig.DEAD_ROUTING_KEY, binding.getRoutingKey())
            );
        }
    }

    // ==========================
    // Converter 测试
    // ==========================

    @Nested
    @DisplayName("消息转换器测试")
    class ConverterTests {

        @Test
        void converter_shouldBeJackson() {
            MessageConverter converter = rabbitMQConfig.messageConverter();

            assertAll(
                    () -> assertNotNull(converter),
                    () -> assertInstanceOf(Jackson2JsonMessageConverter.class, converter)
            );
        }
    }

    // ==========================
    // RabbitTemplate 测试（最终稳定版）
    // ==========================

    @Nested
    @DisplayName("RabbitTemplate 测试")
    class RabbitTemplateTests {

        @Test
        void template_shouldNotBeNull() {
            RabbitTemplate template =
                    rabbitMQConfig.rabbitTemplate(connectionFactory);

            assertNotNull(template);
        }

        @Test
        void template_shouldUseJacksonConverter() {
            RabbitTemplate template =
                    rabbitMQConfig.rabbitTemplate(connectionFactory);

            assertInstanceOf(Jackson2JsonMessageConverter.class,
                    template.getMessageConverter());
        }

        @Test
        void template_shouldUseProvidedConnectionFactory() {
            RabbitTemplate template =
                    rabbitMQConfig.rabbitTemplate(connectionFactory);

            assertSame(connectionFactory,
                    template.getConnectionFactory());
        }
    }

    // ==========================
    // 路由链完整性
    // ==========================

    @Nested
    @DisplayName("路由链完整性验证")
    class RoutingIntegrityTests {

        @Test
        void normalChain_shouldBeConsistent() {
            Binding binding = rabbitMQConfig.chatMemoryBinding();
            Queue queue = rabbitMQConfig.chatMemoryQueue();
            DirectExchange exchange = rabbitMQConfig.chatExchange();

            assertAll(
                    () -> assertEquals(exchange.getName(), binding.getExchange()),
                    () -> assertEquals(queue.getName(), binding.getDestination()),
                    () -> assertEquals(RabbitMQConfig.CHAT_ROUTING_KEY,
                            binding.getRoutingKey())
            );
        }

        @Test
        void deadChain_shouldPointCorrectly() {
            Queue normalQueue = rabbitMQConfig.chatMemoryQueue();
            DirectExchange deadExchange = rabbitMQConfig.chatDeadExchange();
            Binding deadBinding = rabbitMQConfig.deadBinding();

            assertAll(
                    () -> assertEquals(deadExchange.getName(),
                            normalQueue.getArguments().get("x-dead-letter-exchange")),
                    () -> assertEquals(deadBinding.getRoutingKey(),
                            normalQueue.getArguments().get("x-dead-letter-routing-key"))
            );
        }
    }
}