package com.max.ai_agent.mq;

import com.max.ai_agent.dto.ChatMemoryMessage;
import com.max.ai_agent.entity.ChatMemoryEntity;
import com.max.ai_agent.mapper.ChatMemoryMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMemoryConsumer MQ消费者 单元测试")
class ChatMemoryConsumerTest {

    @Mock
    private ChatMemoryMapper chatMemoryMapper;
    @Mock
    private Channel channel;
    @Mock
    private Message amqpMessage;

    @InjectMocks
    private ChatMemoryConsumer consumer;

    private static final long DELIVERY_TAG = 42L;
    private static final String CONV_ID = "conv-test-001";

    private ChatMemoryMessage mqMessage;

    @BeforeEach
    void setUp() {
        mqMessage = ChatMemoryMessage.builder()
                .messageId("msg-001")
                .conversationId(CONV_ID)
                .messageType("USER")
                .content("测试内容")
                .messageOrder(1)
                .timestamp(System.currentTimeMillis())
                .build();

        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(DELIVERY_TAG);
        when(amqpMessage.getMessageProperties()).thenReturn(props);
    }

    @Nested
    @DisplayName("consumeChatMemory() 正常消费")
    class NormalTests {

        @Test
        @DisplayName("正常消费 - 应保存MySQL并ACK")
        void consume_normal_shouldSaveAndAck() throws IOException {
            when(chatMemoryMapper.insert((ChatMemoryEntity) any())).thenReturn(1);

            consumer.consumeChatMemory(mqMessage, amqpMessage, channel);

            verify(chatMemoryMapper).insert(any(ChatMemoryEntity.class));
            verify(channel).basicAck(DELIVERY_TAG, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("正常消费 - Entity字段与MQ消息一致")
        void consume_normal_entityFieldsMatch() throws IOException {
            ArgumentCaptor<ChatMemoryEntity> cap =
                    ArgumentCaptor.forClass(ChatMemoryEntity.class);
            when(chatMemoryMapper.insert(cap.capture())).thenReturn(1);

            consumer.consumeChatMemory(mqMessage, amqpMessage, channel);

            ChatMemoryEntity saved = cap.getValue();
            assertAll(
                () -> assertNull(saved.getId(), "id应为null"),
                () -> assertEquals(CONV_ID, saved.getConversationId()),
                () -> assertEquals("USER", saved.getMessageType()),
                () -> assertEquals("测试内容", saved.getContent()),
                () -> assertEquals(1, saved.getMessageOrder()),
                () -> assertNull(saved.getCreatedTime(), "createdTime由MP填充")
            );
        }

        @Test
        @DisplayName("ASSISTANT消息 - 正确保存")
        void consume_assistant_shouldSave() throws IOException {
            mqMessage.setMessageType("ASSISTANT");
            mqMessage.setContent("AI回复");
            ArgumentCaptor<ChatMemoryEntity> cap =
                    ArgumentCaptor.forClass(ChatMemoryEntity.class);
            when(chatMemoryMapper.insert(cap.capture())).thenReturn(1);

            consumer.consumeChatMemory(mqMessage, amqpMessage, channel);

            assertEquals("ASSISTANT", cap.getValue().getMessageType());
            verify(channel).basicAck(DELIVERY_TAG, false);
        }

        @Test
        @DisplayName("ACK - multiple参数必须为false(单条确认)")
        void consume_ack_multipleMustBeFalse() throws IOException {
            when(chatMemoryMapper.insert((ChatMemoryEntity) any())).thenReturn(1);
            consumer.consumeChatMemory(mqMessage, amqpMessage, channel);
            verify(channel).basicAck(DELIVERY_TAG, false);
        }
    }

    @Nested
    @DisplayName("幂等性测试")
    class IdempotencyTests {

        @Test
        @DisplayName("重复消息 - 应直接ACK不抛异常")
        void consume_duplicate_shouldAckSilently() throws IOException {
            when(chatMemoryMapper.insert((ChatMemoryEntity) any()))
                    .thenThrow(new DuplicateKeyException("Duplicate"));

            assertDoesNotThrow(() ->
                consumer.consumeChatMemory(mqMessage, amqpMessage, channel));
            verify(channel).basicAck(DELIVERY_TAG, false);
        }

        @Test
        @DisplayName("重复消息 - 不应调用basicNack")
        void consume_duplicate_shouldNotNack() throws IOException {
            when(chatMemoryMapper.insert((ChatMemoryEntity) any()))
                    .thenThrow(new DuplicateKeyException("dup"));

            consumer.consumeChatMemory(mqMessage, amqpMessage, channel);

            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("异常重试测试")
    class ExceptionTests {

        @Test
        @DisplayName("DB异常 - 应抛RuntimeException触发Spring重试")
        void consume_dbError_shouldThrow() {
            when(chatMemoryMapper.insert((ChatMemoryEntity) any()))
                    .thenThrow(new RuntimeException("DB超时"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> consumer.consumeChatMemory(mqMessage, amqpMessage, channel));
            assertTrue(ex.getMessage().contains("消息消费失败"));
        }

        @Test
        @DisplayName("DB异常 - 不应ACK")
        void consume_dbError_shouldNotAck() throws IOException {
            when(chatMemoryMapper.insert((ChatMemoryEntity) any()))
                    .thenThrow(new RuntimeException("error"));

            assertThrows(RuntimeException.class,
                    () -> consumer.consumeChatMemory(mqMessage, amqpMessage, channel));
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("DB异常 - 原始异常应被包装为cause")
        void consume_dbError_causeShouldBeWrapped() {
            RuntimeException original = new RuntimeException("原始异常");
            when(chatMemoryMapper.insert((ChatMemoryEntity) any())).thenThrow(original);

            RuntimeException wrapped = assertThrows(RuntimeException.class,
                    () -> consumer.consumeChatMemory(mqMessage, amqpMessage, channel));
            assertEquals(original, wrapped.getCause());
        }
    }

    @Nested
    @DisplayName("consumeDeadLetter() 死信队列")
    class DeadLetterTests {

        @Test
        @DisplayName("死信消费 - 应ACK不抛异常")
        void deadLetter_shouldAckNotThrow() throws IOException {
            assertDoesNotThrow(() ->
                consumer.consumeDeadLetter(mqMessage, amqpMessage, channel));
            verify(channel).basicAck(DELIVERY_TAG, false);
        }

        @Test
        @DisplayName("死信消费 - 不写MySQL")
        void deadLetter_shouldNotWriteDB() throws IOException {
            consumer.consumeDeadLetter(mqMessage, amqpMessage, channel);
            verify(chatMemoryMapper, never()).insert((ChatMemoryEntity) any());
        }

        @Test
        @DisplayName("死信消费 content=null - 不应崩溃")
        void deadLetter_nullContent_shouldNotCrash() throws IOException {
            mqMessage.setContent(null);
            assertDoesNotThrow(() ->
                consumer.consumeDeadLetter(mqMessage, amqpMessage, channel));
            verify(channel).basicAck(DELIVERY_TAG, false);
        }

        @Test
        @DisplayName("死信消费 - ACK的multiple=false")
        void deadLetter_ack_multipleMustBeFalse() throws IOException {
            consumer.consumeDeadLetter(mqMessage, amqpMessage, channel);
            verify(channel).basicAck(DELIVERY_TAG, false);
        }
    }
}