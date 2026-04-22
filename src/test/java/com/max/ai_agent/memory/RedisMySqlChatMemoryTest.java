package com.max.ai_agent.memory;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.max.ai_agent.config.RabbitMQConfig;
import com.max.ai_agent.dto.ChatMemoryMessage;
import com.max.ai_agent.entity.ChatMemoryEntity;
import com.max.ai_agent.mapper.ChatMemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisMySqlChatMemory 核心记忆模块 单元测试")
class RedisMySqlChatMemoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ChatMemoryMapper chatMemoryMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private DefaultRedisScript<Long> addMessageScript;
    @Mock
    private ListOperations<String, Object> listOperations;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RedisMySqlChatMemory chatMemory;

    private static final String CONV_ID = "test-conv-001";
    private static final String REDIS_KEY = "chat:memory:" + CONV_ID;
    private static final String COUNTER_KEY = "chat:counter:" + CONV_ID;

    @BeforeEach
    void setUp() {
        chatMemory = new RedisMySqlChatMemory(
                redisTemplate, chatMemoryMapper,
                rabbitTemplate, addMessageScript, objectMapper
        );
        ReflectionTestUtils.setField(chatMemory, "redisTtl", 3600L);
        ReflectionTestUtils.setField(chatMemory, "maxSize", 100);
    }

    // ==================== add() ====================

    @Nested
    @DisplayName("add() 写入消息测试")
    class AddTests {

        @BeforeEach
        void setup() {
            // 【修改1】删除了 when(redisTemplate.opsForList()).thenReturn(listOperations);
            // 源码 add() 从不调用 opsForList()，该 stub 未被任何 AddTests 测试使用
            // 导致 7 个 UnnecessaryStubbingException
            when(chatMemoryMapper.selectMaxOrder(CONV_ID)).thenReturn(null);
        }

        @Test
        @DisplayName("单条USER消息 - 应写Redis并发MQ")
        void add_single_shouldWriteRedisAndMQ() {
            when(redisTemplate.execute(eq(addMessageScript), anyList(),
                    any(), any(), any())).thenReturn(1L);

            chatMemory.add(CONV_ID, List.of(new UserMessage("你好")));

            verify(redisTemplate).execute(
                    eq(addMessageScript),
                    eq(Arrays.asList(REDIS_KEY, COUNTER_KEY)),
                    any(), any(), any());
            verify(rabbitTemplate).convertAndSend(
                    eq(RabbitMQConfig.CHAT_EXCHANGE),
                    eq(RabbitMQConfig.CHAT_ROUTING_KEY),
                    any(ChatMemoryMessage.class));
        }

        @Test
        @DisplayName("多条消息 - 应循环写入每条")
        void add_multiple_shouldWriteEach() {
            when(redisTemplate.execute(eq(addMessageScript), anyList(),
                    any(), any(), any())).thenReturn(1L, 2L, 3L);

            chatMemory.add(CONV_ID, List.of(
                    new UserMessage("第一条"),
                    new AssistantMessage("回复"),
                    new UserMessage("第二条")));

            verify(redisTemplate, times(3)).execute(
                    eq(addMessageScript), anyList(), any(), any(), any());
            verify(rabbitTemplate, times(3)).convertAndSend(
                    anyString(), anyString(), any(ChatMemoryMessage.class));
        }

        @Test
        @DisplayName("MySQL有历史order=5 - maxOrder参数应传'5'")
        void add_withExistingOrder_shouldPassCorrectMaxOrder() {
            when(chatMemoryMapper.selectMaxOrder(CONV_ID)).thenReturn(5);
            when(redisTemplate.execute(eq(addMessageScript), anyList(),
                    any(), any(), any())).thenReturn(6L);

            chatMemory.add(CONV_ID, List.of(new UserMessage("新消息")));

            verify(redisTemplate).execute(
                    eq(addMessageScript), anyList(),
                    anyString(), anyString(), eq("5"));
        }

        @Test
        @DisplayName("MQ消息 - USER类型字段应全部正确")
        void add_mqMessage_userFields() {
            when(redisTemplate.execute(eq(addMessageScript), anyList(),
                    any(), any(), any())).thenReturn(1L);

            chatMemory.add(CONV_ID, List.of(new UserMessage("测试内容")));

            ArgumentCaptor<ChatMemoryMessage> cap =
                    ArgumentCaptor.forClass(ChatMemoryMessage.class);
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), cap.capture());

            ChatMemoryMessage sent = cap.getValue();
            assertAll(
                    () -> assertEquals("USER", sent.getMessageType()),
                    () -> assertEquals("测试内容", sent.getContent()),
                    () -> assertEquals(CONV_ID, sent.getConversationId()),
                    () -> assertEquals(1, sent.getMessageOrder()),
                    () -> assertNotNull(sent.getMessageId())
            );
        }

        @Test
        @DisplayName("MQ消息 - ASSISTANT类型")
        void add_mqMessage_assistantType() {
            when(redisTemplate.execute(eq(addMessageScript), anyList(),
                    any(), any(), any())).thenReturn(1L);
            chatMemory.add(CONV_ID, List.of(new AssistantMessage("AI回复")));
            ArgumentCaptor<ChatMemoryMessage> cap =
                    ArgumentCaptor.forClass(ChatMemoryMessage.class);
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), cap.capture());
            assertEquals("ASSISTANT", cap.getValue().getMessageType());
        }

        @Test
        @DisplayName("MQ消息 - SYSTEM类型")
        void add_mqMessage_systemType() {
            when(redisTemplate.execute(eq(addMessageScript), anyList(),
                    any(), any(), any())).thenReturn(1L);
            chatMemory.add(CONV_ID, List.of(new SystemMessage("系统提示")));
            ArgumentCaptor<ChatMemoryMessage> cap =
                    ArgumentCaptor.forClass(ChatMemoryMessage.class);
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), cap.capture());
            assertEquals("SYSTEM", cap.getValue().getMessageType());
        }

        @Test
        @DisplayName("Lua返回null - 应抛RuntimeException，不发MQ")
        void add_luaNull_shouldThrowAndNotSendMQ() {
            when(redisTemplate.execute(eq(addMessageScript), anyList(),
                    any(), any(), any())).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> chatMemory.add(CONV_ID, List.of(new UserMessage("test"))));
            assertEquals("Redis写入失败", ex.getMessage());
            verify(rabbitTemplate, never()).convertAndSend(
                    anyString(), anyString(), any(Object.class));
        }

        @Test
        @DisplayName("空消息列表 - 不应调用任何依赖")
        void add_empty_shouldCallNothing() {
            chatMemory.add(CONV_ID, Collections.emptyList());
            verify(redisTemplate, never()).execute(
                    any(DefaultRedisScript.class), anyList(), any());
            verify(rabbitTemplate, never()).convertAndSend(
                    anyString(), anyString(), any(Object.class));
        }
    }

    // ==================== get() ====================

    @Nested
    @DisplayName("get() 获取消息测试")
    class GetTests {

        @BeforeEach
        void setup() {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
        }

        @Test
        @DisplayName("Redis命中 - 应从Redis返回，不查MySQL")
        void get_redisHit_shouldFromRedis() throws Exception {
            ChatMemoryMessage m1 = buildMsg("USER", "你好", 1);
            ChatMemoryMessage m2 = buildMsg("ASSISTANT", "你好！", 2);

            when(listOperations.size(REDIS_KEY)).thenReturn(2L);
            when(listOperations.range(REDIS_KEY, 0L, -1L)).thenReturn(
                    List.of(objectMapper.writeValueAsString(m1),
                            objectMapper.writeValueAsString(m2)));

            List<Message> result = chatMemory.get(CONV_ID, 10);

            assertEquals(2, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
            assertInstanceOf(AssistantMessage.class, result.get(1));
            verify(chatMemoryMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("Redis命中 lastN=1 - 应只返回最后1条")
        void get_redisHit_lastN1() throws Exception {
            ChatMemoryMessage m2 = buildMsg("ASSISTANT", "第2条", 2);

            when(listOperations.size(REDIS_KEY)).thenReturn(2L);
            when(listOperations.range(REDIS_KEY, 1L, -1L)).thenReturn(
                    List.of(objectMapper.writeValueAsString(m2)));

            List<Message> result = chatMemory.get(CONV_ID, 1);

            assertEquals(1, result.size());
            verify(listOperations).range(REDIS_KEY, 1L, -1L);
        }

        @Test
        @DisplayName("Redis未命中(size=0) - 应查MySQL")
        void get_redisMiss_shouldQueryMySQL() {
            when(listOperations.size(REDIS_KEY)).thenReturn(0L);

            // 【修改2】源码 loadFromMySQL 用的是参数 page.getRecords()，不是返回值
            // 必须用 thenAnswer 把数据写进参数 page，否则 records 始终为空
            when(chatMemoryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
                Page<ChatMemoryEntity> p = invocation.getArgument(0);
                p.setRecords(Collections.emptyList());
                return p;
            });

            List<Message> result = chatMemory.get(CONV_ID, 10);

            assertTrue(result.isEmpty());
            verify(chatMemoryMapper).selectPage(any(), any());
        }

        @Test
        @DisplayName("Redis未命中，MySQL有数据 - 应回填Redis")
        void get_redisMiss_mysqlData_shouldBackfill() {
            when(listOperations.size(REDIS_KEY)).thenReturn(0L);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            ChatMemoryEntity e1 = buildEntity(1L, "USER", "你好", 1);
            ChatMemoryEntity e2 = buildEntity(2L, "ASSISTANT", "好！", 2);

            // 【修改2·关键】源码 loadFromMySQL 第113行：
            //   chatMemoryMapper.selectPage(page, wrapper);
            //   List<ChatMemoryEntity> entities = page.getRecords();  ← 用的是参数 page！
            // 用 thenReturn 不会修改参数对象，参数 page 的 records 始终为空
            // → entities.isEmpty() = true → 直接 return → 回填逻辑整个被跳过
            // → rightPush 从未被调用 → Lua脚本也从未执行（日志报"Lua脚本执行失败"与此无关，
            //   那是 add() 的日志，只是恰好在同一会话ID上触发）
            when(chatMemoryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
                Page<ChatMemoryEntity> p = invocation.getArgument(0);
                p.setRecords(Arrays.asList(e2, e1));  // 模拟按 order DESC 排列
                return p;
            });
            when(chatMemoryMapper.selectMaxOrder(CONV_ID)).thenReturn(2);

            List<Message> result = chatMemory.get(CONV_ID, 10);

            // 源码 loadFromMySQL 用 redisTemplate.opsForList().rightPush() 回填
            verify(listOperations, times(2)).rightPush(eq(REDIS_KEY), anyString());
            verify(redisTemplate).expire(eq(REDIS_KEY), eq(3600L), any());
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("Redis size=null - 应查MySQL")
        void get_redisSizeNull_shouldQueryMySQL() {
            when(listOperations.size(REDIS_KEY)).thenReturn(null);

            // 【修改2】同上，用 thenAnswer
            when(chatMemoryMapper.selectPage(any(), any())).thenAnswer(invocation -> {
                Page<ChatMemoryEntity> p = invocation.getArgument(0);
                p.setRecords(Collections.emptyList());
                return p;
            });

            List<Message> result = chatMemory.get(CONV_ID, 10);

            assertTrue(result.isEmpty());
            verify(chatMemoryMapper).selectPage(any(), any());
        }

        @Test
        @DisplayName("Redis有脏数据 - 应过滤坏数据")
        void get_dirtyData_shouldFilter() throws Exception {
            ChatMemoryMessage good = buildMsg("USER", "正常消息", 1);
            String goodJson = objectMapper.writeValueAsString(good);
            String badJson = "不合法JSON%%%";

            when(listOperations.size(REDIS_KEY)).thenReturn(2L);
            when(listOperations.range(REDIS_KEY, 0L, -1L))
                    .thenReturn(List.of(goodJson, badJson));

            List<Message> result = chatMemory.get(CONV_ID, 10);

            assertEquals(1, result.size());
            assertInstanceOf(UserMessage.class, result.get(0));
        }

        @Test
        @DisplayName("Redis消息类型UNKNOWN - 应被过滤")
        void get_unknownType_shouldFilter() throws Exception {
            ChatMemoryMessage unknown = buildMsg("UNKNOWN", "未知", 1);
            String json = objectMapper.writeValueAsString(unknown);

            when(listOperations.size(REDIS_KEY)).thenReturn(1L);
            when(listOperations.range(REDIS_KEY, 0L, -1L))
                    .thenReturn(List.of(json));

            List<Message> result = chatMemory.get(CONV_ID, 10);

            assertTrue(result.isEmpty(), "UNKNOWN类型应被过滤");
        }
    }

    // ==================== clear() ====================

    @Nested
    @DisplayName("clear() 清除测试")
    class ClearTests {

        @Test
        @DisplayName("clear() - 应删除Redis两个key和MySQL数据")
        void clear_shouldDeleteAll() {
            chatMemory.clear(CONV_ID);
            verify(redisTemplate).delete(REDIS_KEY);
            verify(redisTemplate).delete(COUNTER_KEY);
            verify(chatMemoryMapper).delete(any());
        }

        @Test
        @DisplayName("clear() 不同会话 - 只清指定会话的key")
        void clear_shouldDeleteCorrectKeys() {
            String otherId = "other-conv-999";
            chatMemory.clear(otherId);
            verify(redisTemplate).delete("chat:memory:" + otherId);
            verify(redisTemplate).delete("chat:counter:" + otherId);
        }

        @Test
        @DisplayName("clear() - 不应删除其他会话的key")
        void clear_shouldNotDeleteOtherConvKeys() {
            chatMemory.clear(CONV_ID);
            verify(redisTemplate, never()).delete("chat:memory:other-conv");
            verify(redisTemplate, never()).delete("chat:counter:other-conv");
        }
    }

    // ==================== 工具方法 ====================

    private ChatMemoryMessage buildMsg(String type, String content, int order) {
        return ChatMemoryMessage.builder()
                .conversationId(CONV_ID).messageType(type)
                .content(content).messageOrder(order)
                .timestamp(System.currentTimeMillis())
                .messageId(UUID.randomUUID().toString())
                .build();
    }

    private ChatMemoryEntity buildEntity(Long id, String type,
                                         String content, int order) {
        return ChatMemoryEntity.builder()
                .id(id).conversationId(CONV_ID).messageType(type)
                .content(content).messageOrder(order).build();
    }
}