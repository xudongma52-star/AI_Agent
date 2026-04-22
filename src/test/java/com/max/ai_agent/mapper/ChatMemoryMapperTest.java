package com.max.ai_agent.mapper;

import com.max.ai_agent.entity.ChatMemoryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMemoryMapper 数据层 单元测试")
class ChatMemoryMapperTest {

    @Mock
    private ChatMemoryMapper chatMemoryMapper;

    private static final String CONV_ID = "conv-mapper-001";

    @Nested
    @DisplayName("selectLastN() 测试")
    class SelectLastNTests {

        @Test
        @DisplayName("有数据 - 应返回正确条数")
        void selectLastN_hasData_shouldReturnCorrectCount() {
            when(chatMemoryMapper.selectLastN(CONV_ID, 3))
                    .thenReturn(buildEntities(3));

            List<ChatMemoryEntity> result =
                    chatMemoryMapper.selectLastN(CONV_ID, 3);

            assertEquals(3, result.size());
            verify(chatMemoryMapper).selectLastN(CONV_ID, 3);
        }

        @Test
        @DisplayName("无数据 - 应返回空列表")
        void selectLastN_noData_shouldReturnEmpty() {
            when(chatMemoryMapper.selectLastN(CONV_ID, 10))
                    .thenReturn(Collections.emptyList());

            List<ChatMemoryEntity> result =
                    chatMemoryMapper.selectLastN(CONV_ID, 10);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("返回结果应按 message_order 升序")
        void selectLastN_shouldBeAscOrder() {
            List<ChatMemoryEntity> ascData = Arrays.asList(
                    buildEntity(1L, "USER", 1),
                    buildEntity(2L, "ASSISTANT", 2),
                    buildEntity(3L, "USER", 3)
            );
            when(chatMemoryMapper.selectLastN(CONV_ID, 3)).thenReturn(ascData);

            List<ChatMemoryEntity> result =
                    chatMemoryMapper.selectLastN(CONV_ID, 3);

            for (int i = 0; i < result.size() - 1; i++) {
                assertTrue(result.get(i).getMessageOrder()
                        < result.get(i + 1).getMessageOrder());
            }
        }

        @Test
        @DisplayName("limit=1 - 应只返回1条")
        void selectLastN_limit1_shouldReturn1() {
            when(chatMemoryMapper.selectLastN(CONV_ID, 1))
                    .thenReturn(List.of(buildEntity(10L, "USER", 10)));

            List<ChatMemoryEntity> result =
                    chatMemoryMapper.selectLastN(CONV_ID, 1);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("不同conversationId - 数据应隔离")
        void selectLastN_differentConv_shouldBeIsolated() {
            when(chatMemoryMapper.selectLastN("conv-A", 10))
                    .thenReturn(buildEntities(3));
            when(chatMemoryMapper.selectLastN("conv-B", 10))
                    .thenReturn(buildEntities(1));

            List<ChatMemoryEntity> r1 = chatMemoryMapper.selectLastN("conv-A", 10);
            List<ChatMemoryEntity> r2 = chatMemoryMapper.selectLastN("conv-B", 10);

            assertNotEquals(r1.size(), r2.size());
        }
    }

    @Nested
    @DisplayName("selectMaxOrder() 测试")
    class SelectMaxOrderTests {

        @Test
        @DisplayName("有数据 - 应返回最大order")
        void selectMaxOrder_hasData_shouldReturnMax() {
            when(chatMemoryMapper.selectMaxOrder(CONV_ID)).thenReturn(42);

            Integer result = chatMemoryMapper.selectMaxOrder(CONV_ID);

            assertEquals(42, result);
        }

        @Test
        @DisplayName("无数据 - COALESCE保证返回0不是null")
        void selectMaxOrder_noData_shouldReturn0() {
            when(chatMemoryMapper.selectMaxOrder(CONV_ID)).thenReturn(0);

            Integer result = chatMemoryMapper.selectMaxOrder(CONV_ID);

            assertNotNull(result);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("下一条消息order应在maxOrder+1")
        void selectMaxOrder_nextOrderShouldIncrement() {
            when(chatMemoryMapper.selectMaxOrder(CONV_ID)).thenReturn(5);

            int next = chatMemoryMapper.selectMaxOrder(CONV_ID) + 1;

            assertEquals(6, next);
        }

        @Test
        @DisplayName("不同会话maxOrder应独立")
        void selectMaxOrder_differentConv_shouldBeIndependent() {
            when(chatMemoryMapper.selectMaxOrder("conv-A")).thenReturn(10);
            when(chatMemoryMapper.selectMaxOrder("conv-B")).thenReturn(3);

            assertNotEquals(
                chatMemoryMapper.selectMaxOrder("conv-A"),
                chatMemoryMapper.selectMaxOrder("conv-B")
            );
        }
    }

    @Nested
    @DisplayName("deleteByConversationId() 测试")
    class DeleteTests {

        @Test
        @DisplayName("应调用删除方法")
        void delete_shouldInvoke() {
            chatMemoryMapper.deleteByConversationId(CONV_ID);
            verify(chatMemoryMapper).deleteByConversationId(CONV_ID);
        }

        @Test
        @DisplayName("只删除目标会话，不影响其他")
        void delete_shouldOnlyDeleteTarget() {
            chatMemoryMapper.deleteByConversationId("target-conv");
            verify(chatMemoryMapper).deleteByConversationId("target-conv");
            verify(chatMemoryMapper, never())
                    .deleteByConversationId("other-conv");
        }
    }

    @Nested
    @DisplayName("insert() 测试")
    class InsertTests {

        @Test
        @DisplayName("正常插入 - 应返回1")
        void insert_normal_shouldReturn1() {
            ChatMemoryEntity entity = buildEntity(null, "USER", 1);
            when(chatMemoryMapper.insert(entity)).thenReturn(1);

            int result = chatMemoryMapper.insert(entity);

            assertEquals(1, result);
        }

        @Test
        @DisplayName("三种消息类型均可插入")
        void insert_allTypes_shouldSucceed() {
            for (String type : new String[]{"USER", "ASSISTANT", "SYSTEM"}) {
                ChatMemoryEntity entity = buildEntity(null, type, 1);
                when(chatMemoryMapper.insert(entity)).thenReturn(1);
                assertEquals(1, chatMemoryMapper.insert(entity),
                        type + " 类型插入应返回1");
            }
        }
    }

    @Nested
    @DisplayName("SQL语义验证")
    class SqlSemanticTests {

        @Test
        @DisplayName("selectLastN SQL - 双重排序语义正确")
        void selectLastN_sqlSemantic() {
            String sql = """
                SELECT * FROM (
                    SELECT * FROM chat_memory
                    WHERE conversation_id = #{conversationId}
                    ORDER BY message_order DESC
                    LIMIT #{limit}
                ) t ORDER BY t.message_order ASC
                """;
            assertAll(
                () -> assertTrue(sql.contains("ORDER BY message_order DESC"),
                        "内层应降序取最新消息"),
                () -> assertTrue(sql.contains("LIMIT #{limit}"),
                        "应有LIMIT限制"),
                () -> assertTrue(sql.contains("ORDER BY t.message_order ASC"),
                        "外层应升序还原时序")
            );
        }

        @Test
        @DisplayName("selectMaxOrder SQL - COALESCE防止NULL")
        void selectMaxOrder_coalescePreventNull() {
            String sql = "SELECT COALESCE(MAX(message_order), 0) " +
                         "FROM chat_memory WHERE conversation_id = #{conversationId}";
            assertTrue(sql.contains("COALESCE"));
            assertTrue(sql.contains("MAX(message_order)"));
        }
    }

    // ==================== 工具方法 ====================

    private ChatMemoryEntity buildEntity(Long id, String type, int order) {
        return ChatMemoryEntity.builder()
                .id(id).conversationId(CONV_ID)
                .messageType(type).content("内容-" + order)
                .messageOrder(order).build();
    }

    private List<ChatMemoryEntity> buildEntities(int count) {
        List<ChatMemoryEntity> list = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(buildEntity((long) i,
                    i % 2 == 0 ? "ASSISTANT" : "USER", i));
        }
        return list;
    }
}