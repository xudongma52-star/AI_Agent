package com.max.ai_agent.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatMemoryMessage DTO 单元测试")
class ChatMemoryMessageTest {

    @Nested
    @DisplayName("Builder 测试")
    class BuilderTests {

        @Test
        @DisplayName("Builder 全参构建 - 所有字段应正确赋值")
        void builder_allFields_correct() {
            ChatMemoryMessage msg = ChatMemoryMessage.builder()
                    .conversationId("conv-001")
                    .messageType("USER")
                    .content("你好，AI！")
                    .messageOrder(1)
                    .timestamp(1700000000000L)
                    .messageId("msg-uuid-001")
                    .build();

            assertAll(
                    () -> assertEquals("conv-001", msg.getConversationId()),
                    () -> assertEquals("USER", msg.getMessageType()),
                    () -> assertEquals("你好，AI！", msg.getContent()),
                    () -> assertEquals(1, msg.getMessageOrder()),
                    () -> assertEquals(1700000000000L, msg.getTimestamp()),
                    () -> assertEquals("msg-uuid-001", msg.getMessageId())
            );
        }

        @Test
        @DisplayName("Builder 部分字段 - 未设置字段应为null")
        void builder_partial_unsetShouldBeNull() {
            ChatMemoryMessage msg = ChatMemoryMessage.builder()
                    .conversationId("conv-001")
                    .build();

            assertAll(
                    () -> assertEquals("conv-001", msg.getConversationId()),
                    () -> assertNull(msg.getMessageType()),
                    () -> assertNull(msg.getMessageId())
            );
        }

        @Test
        @DisplayName("Builder 空构建 - 全部为null")
        void builder_empty_allNull() {
            ChatMemoryMessage msg = ChatMemoryMessage.builder().build();
            assertAll(
                    () -> assertNull(msg.getConversationId()),
                    () -> assertNull(msg.getMessageType()),
                    () -> assertNull(msg.getContent()),
                    () -> assertNull(msg.getMessageOrder()),
                    () -> assertNull(msg.getTimestamp()),
                    () -> assertNull(msg.getMessageId())
            );
        }
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造 - 对象应正常创建")
        void noArgs_shouldCreateObject() {
            ChatMemoryMessage msg = new ChatMemoryMessage();
            assertNotNull(msg);
            assertNull(msg.getConversationId());
        }

        @Test
        @DisplayName("全参构造 - 所有字段正确")
        void allArgs_shouldSetAll() {
            ChatMemoryMessage msg = new ChatMemoryMessage(
                    "conv-002", "ASSISTANT", "我是AI",
                    2, 1700000001000L, "msg-002"
            );
            assertAll(
                    () -> assertEquals("conv-002", msg.getConversationId()),
                    () -> assertEquals("ASSISTANT", msg.getMessageType()),
                    () -> assertEquals("我是AI", msg.getContent()),
                    () -> assertEquals(2, msg.getMessageOrder()),
                    () -> assertEquals(1700000001000L, msg.getTimestamp()),
                    () -> assertEquals("msg-002", msg.getMessageId())
            );
        }
    }

    @Nested
    @DisplayName("Setter & Getter 测试")
    class SetterGetterTests {

        @Test
        @DisplayName("Setter - 应能修改所有字段")
        void setters_shouldUpdateAllFields() {
            ChatMemoryMessage msg = new ChatMemoryMessage();
            msg.setConversationId("new-conv");
            msg.setMessageType("SYSTEM");
            msg.setContent("系统消息");
            msg.setMessageOrder(0);
            msg.setTimestamp(9999999999L);
            msg.setMessageId("new-id");

            assertAll(
                    () -> assertEquals("new-conv", msg.getConversationId()),
                    () -> assertEquals("SYSTEM", msg.getMessageType()),
                    () -> assertEquals("系统消息", msg.getContent()),
                    () -> assertEquals(0, msg.getMessageOrder()),
                    () -> assertEquals(9999999999L, msg.getTimestamp()),
                    () -> assertEquals("new-id", msg.getMessageId())
            );
        }
    }

    @Nested
    @DisplayName("equals & hashCode 测试")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("相同字段 - 应相等")
        void equals_sameFields_equal() {
            ChatMemoryMessage m1 = ChatMemoryMessage.builder()
                    .conversationId("c1").messageId("m1").content("test").build();
            ChatMemoryMessage m2 = ChatMemoryMessage.builder()
                    .conversationId("c1").messageId("m1").content("test").build();
            assertEquals(m1, m2);
            assertEquals(m1.hashCode(), m2.hashCode());
        }

        @Test
        @DisplayName("不同messageId - 不应相等")
        void equals_differentId_notEqual() {
            ChatMemoryMessage m1 = ChatMemoryMessage.builder()
                    .messageId("m1").build();
            ChatMemoryMessage m2 = ChatMemoryMessage.builder()
                    .messageId("m2").build();
            assertNotEquals(m1, m2);
        }
    }

    @Nested
    @DisplayName("业务场景测试")
    class BusinessTests {

        @Test
        @DisplayName("USER消息 - messageId不应为null（幂等性）")
        void userMessage_messageIdNotNull() {
            ChatMemoryMessage msg = ChatMemoryMessage.builder()
                    .conversationId("session-123")
                    .messageType("USER")
                    .content("帮我写代码")
                    .messageOrder(1)
                    .timestamp(System.currentTimeMillis())
                    .messageId(UUID.randomUUID().toString())
                    .build();

            assertNotNull(msg.getMessageId());
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("ASSISTANT消息order - 应大于USER消息")
        void assistantOrder_greaterThanUser() {
            ChatMemoryMessage user = ChatMemoryMessage.builder()
                    .messageType("USER").messageOrder(1).build();
            ChatMemoryMessage ai = ChatMemoryMessage.builder()
                    .messageType("ASSISTANT").messageOrder(2).build();

            assertTrue(ai.getMessageOrder() > user.getMessageOrder());
        }

        @Test
        @DisplayName("timestamp - 应为正数")
        void timestamp_shouldBePositive() {
            ChatMemoryMessage msg = ChatMemoryMessage.builder()
                    .timestamp(System.currentTimeMillis())
                    .build();
            assertTrue(msg.getTimestamp() > 0);
        }
    }
}