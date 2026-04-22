package com.max.ai_agent.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChatMemoryEntity 实体类 单元测试")
class ChatMemoryEntityTest {

    @Nested
    @DisplayName("Builder 测试")
    class BuilderTests {

        @Test
        @DisplayName("Builder 全参 - 字段应正确赋值")
        void builder_allFields() {
            LocalDateTime now = LocalDateTime.now();
            ChatMemoryEntity entity = ChatMemoryEntity.builder()
                    .id(1L)
                    .conversationId("conv-001")
                    .messageType("USER")
                    .content("内容")
                    .messageOrder(1)
                    .createdTime(now)
                    .build();

            assertAll(
                () -> assertEquals(1L, entity.getId()),
                () -> assertEquals("conv-001", entity.getConversationId()),
                () -> assertEquals("USER", entity.getMessageType()),
                () -> assertEquals("内容", entity.getContent()),
                () -> assertEquals(1, entity.getMessageOrder()),
                () -> assertEquals(now, entity.getCreatedTime())
            );
        }

        @Test
        @DisplayName("id=null - 模拟数据库自增场景")
        void builder_nullId_simulatesAutoIncrement() {
            ChatMemoryEntity entity = ChatMemoryEntity.builder()
                    .id(null)
                    .conversationId("conv-002")
                    .messageType("ASSISTANT")
                    .content("AI回复")
                    .messageOrder(2)
                    .build();

            assertNull(entity.getId(), "插入前id应为null");
            assertNotNull(entity.getConversationId());
        }

        @Test
        @DisplayName("Builder 空构建 - 全部为null")
        void builder_empty_allNull() {
            ChatMemoryEntity entity = ChatMemoryEntity.builder().build();
            assertAll(
                () -> assertNull(entity.getId()),
                () -> assertNull(entity.getConversationId()),
                () -> assertNull(entity.getMessageType()),
                () -> assertNull(entity.getContent()),
                () -> assertNull(entity.getMessageOrder()),
                () -> assertNull(entity.getCreatedTime())
            );
        }
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造 - 对象正常创建")
        void noArgs_shouldCreateObject() {
            ChatMemoryEntity entity = new ChatMemoryEntity();
            assertNotNull(entity);
            assertNull(entity.getId());
        }

        @Test
        @DisplayName("全参构造 - 所有字段正确")
        void allArgs_shouldSetAll() {
            LocalDateTime now = LocalDateTime.now();
            ChatMemoryEntity entity = new ChatMemoryEntity(
                    1L, "conv-001", "USER", "测试内容", 1, now
            );
            assertAll(
                () -> assertEquals(1L, entity.getId()),
                () -> assertEquals("conv-001", entity.getConversationId()),
                () -> assertEquals("USER", entity.getMessageType()),
                () -> assertEquals("测试内容", entity.getContent()),
                () -> assertEquals(1, entity.getMessageOrder()),
                () -> assertEquals(now, entity.getCreatedTime())
            );
        }
    }

    @Nested
    @DisplayName("消息类型验证")
    class MessageTypeTests {

        @Test
        @DisplayName("USER类型 - 存储正确")
        void type_USER() {
            ChatMemoryEntity entity = ChatMemoryEntity.builder()
                    .messageType("USER").build();
            assertEquals("USER", entity.getMessageType());
        }

        @Test
        @DisplayName("ASSISTANT类型 - 存储正确")
        void type_ASSISTANT() {
            ChatMemoryEntity entity = ChatMemoryEntity.builder()
                    .messageType("ASSISTANT").build();
            assertEquals("ASSISTANT", entity.getMessageType());
        }

        @Test
        @DisplayName("SYSTEM类型 - 存储正确")
        void type_SYSTEM() {
            ChatMemoryEntity entity = ChatMemoryEntity.builder()
                    .messageType("SYSTEM").build();
            assertEquals("SYSTEM", entity.getMessageType());
        }
    }

    @Nested
    @DisplayName("Setter 测试")
    class SetterTests {

        @Test
        @DisplayName("Setter - 应能修改所有字段")
        void setters_shouldUpdateAll() {
            ChatMemoryEntity entity = new ChatMemoryEntity();
            LocalDateTime now = LocalDateTime.now();

            entity.setId(99L);
            entity.setConversationId("new-conv");
            entity.setMessageType("SYSTEM");
            entity.setContent("新内容");
            entity.setMessageOrder(10);
            entity.setCreatedTime(now);

            assertAll(
                () -> assertEquals(99L, entity.getId()),
                () -> assertEquals("new-conv", entity.getConversationId()),
                () -> assertEquals("SYSTEM", entity.getMessageType()),
                () -> assertEquals("新内容", entity.getContent()),
                () -> assertEquals(10, entity.getMessageOrder()),
                () -> assertEquals(now, entity.getCreatedTime())
            );
        }
    }

    @Nested
    @DisplayName("equals & hashCode 测试")
    class EqualsTests {

        @Test
        @DisplayName("相同字段 - 应相等")
        void equals_sameFields() {
            ChatMemoryEntity e1 = ChatMemoryEntity.builder()
                    .id(1L).conversationId("c1").messageType("USER").build();
            ChatMemoryEntity e2 = ChatMemoryEntity.builder()
                    .id(1L).conversationId("c1").messageType("USER").build();
            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("不同id - 不应相等")
        void equals_differentId() {
            ChatMemoryEntity e1 = ChatMemoryEntity.builder().id(1L).build();
            ChatMemoryEntity e2 = ChatMemoryEntity.builder().id(2L).build();
            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("toString - 不应为null")
        void toString_shouldNotBeNull() {
            ChatMemoryEntity entity = ChatMemoryEntity.builder()
                    .id(1L).conversationId("c1").build();
            assertNotNull(entity.toString());
            assertTrue(entity.toString().contains("conv-001")
                    || entity.toString().contains("c1"));
        }
    }

    @Nested
    @DisplayName("业务场景测试")
    class BusinessTests {

        @Test
        @DisplayName("messageOrder - 应为正整数")
        void messageOrder_shouldBePositive() {
            ChatMemoryEntity entity = ChatMemoryEntity.builder()
                    .messageOrder(1).build();
            assertTrue(entity.getMessageOrder() > 0);
        }

        @Test
        @DisplayName("同一会话多条消息 - order应递增")
        void sameConversation_orderShouldIncrement() {
            ChatMemoryEntity first = ChatMemoryEntity.builder()
                    .conversationId("conv-001").messageOrder(1).build();
            ChatMemoryEntity second = ChatMemoryEntity.builder()
                    .conversationId("conv-001").messageOrder(2).build();

            assertEquals(first.getConversationId(), second.getConversationId());
            assertTrue(second.getMessageOrder() > first.getMessageOrder());
        }

        @Test
        @DisplayName("createdTime为null - 模拟MP自动填充前的状态")
        void createdTime_nullBeforeInsert() {
            ChatMemoryEntity entity = ChatMemoryEntity.builder()
                    .conversationId("conv-001")
                    .messageType("USER")
                    .content("内容")
                    .messageOrder(1)
                    .build();
            // createdTime 未设置，应为null，由 MP @TableField(fill=INSERT) 自动填充
            assertNull(entity.getCreatedTime(),
                    "插入前createdTime应为null，由MyBatisPlus自动填充");
        }
    }
}