// memory/RedisMySqlChatMemory.java
package com.max.ai_agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.max.ai_agent.dto.ChatMessageDTO;
import com.max.ai_agent.entity.ChatMemoryEntity;
import com.max.ai_agent.mapper.ChatMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisMySqlChatMemory implements ChatMemory {

    private static final String REDIS_KEY_PREFIX = "chat:memory:";
    private static final String COUNTER_KEY_PREFIX = "chat:counter:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatMemoryMapper chatMemoryMapper;

    @Value("${chat.memory.redis-ttl:3600}")
    private long redisTtl;

    @Value("${chat.memory.max-size:100}")
    private int maxSize;

    public RedisMySqlChatMemory(RedisTemplate<String, Object> redisTemplate,
                                 ChatMemoryMapper chatMemoryMapper) {
        this.redisTemplate = redisTemplate;
        this.chatMemoryMapper = chatMemoryMapper;
    }

    /**
     * 写入消息
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        String redisKey = REDIS_KEY_PREFIX + conversationId;
        String counterKey = COUNTER_KEY_PREFIX + conversationId;

        for (Message message : messages) {
            // 原子自增获取顺序号
            Long order = redisTemplate.opsForValue().increment(counterKey);
            if (order == null || order == 1) {
                // Redis没有，从MySQL补偿最大值
                Integer maxOrder = chatMemoryMapper.selectMaxOrder(conversationId);
                order = (maxOrder == null ? 0 : maxOrder) + 1L;
                redisTemplate.opsForValue().set(counterKey, order);
            }

            ChatMessageDTO dto = new ChatMessageDTO(
                    getMessageType(message),
                    message.getText(),
                    order.intValue(),
                    System.currentTimeMillis()
            );

            // 同步写Redis
            redisTemplate.opsForList().rightPush(redisKey, dto);
            redisTemplate.expire(redisKey, redisTtl, TimeUnit.SECONDS);
            redisTemplate.expire(counterKey, redisTtl, TimeUnit.SECONDS);

            // 异步写MySQL
            asyncSaveToMySQL(conversationId, dto);
        }

        log.debug("写入{}条消息，会话[{}]", messages.size(), conversationId);
    }

    /**
     * 读取消息
     */
    @Override
    public List<Message> get(String conversationId, int lastN) {
        String redisKey = REDIS_KEY_PREFIX + conversationId;
        Long redisSize = redisTemplate.opsForList().size(redisKey);

        // Redis有数据直接返回
        if (redisSize != null && redisSize > 0) {
            log.debug("Redis命中，会话[{}] 共{}条", conversationId, redisSize);
            return getFromRedis(redisKey, lastN);
        }

        // Redis没有，去MySQL查并回填
        log.debug("Redis未命中，从MySQL加载，会话[{}]", conversationId);
        return loadFromMySQL(conversationId, lastN, redisKey);
    }

    /**
     * 清除会话
     */
    @Override
    public void clear(String conversationId) {
        // 清Redis
        redisTemplate.delete(REDIS_KEY_PREFIX + conversationId);
        redisTemplate.delete(COUNTER_KEY_PREFIX + conversationId);

        // 清MySQL（MP的lambdaDelete）
        chatMemoryMapper.delete(
                new LambdaQueryWrapper<ChatMemoryEntity>()
                        .eq(ChatMemoryEntity::getConversationId, conversationId)
        );

        log.info("已清除会话[{}]所有记忆", conversationId);
    }

    // ========== 私有方法 ==========

    /**
     * 从Redis获取最后N条
     */
    private List<Message> getFromRedis(String redisKey, int lastN) {
        try {
            Long size = redisTemplate.opsForList().size(redisKey);
            if (size == null || size == 0) return Collections.emptyList();

            long start = Math.max(0, size - lastN);
            List<Object> rawList = redisTemplate.opsForList().range(redisKey, start, -1);
            if (rawList == null) return Collections.emptyList();

            return rawList.stream()
                    .filter(obj -> obj instanceof ChatMessageDTO)
                    .map(obj -> convertToMessage((ChatMessageDTO) obj))
                    .filter(msg -> msg != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Redis获取消息失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从MySQL加载并回填Redis
     */
    private List<Message> loadFromMySQL(String conversationId,
                                                  int lastN, String redisKey) {
        try {
            // MP的LambdaQuery查询
            List<ChatMemoryEntity> entities = chatMemoryMapper.selectList(
                    new LambdaQueryWrapper<ChatMemoryEntity>()
                            .eq(ChatMemoryEntity::getConversationId, conversationId)
                            .orderByAsc(ChatMemoryEntity::getMessageOrder)
                            .last("LIMIT " + maxSize)
            );

            if (entities.isEmpty()) return Collections.emptyList();

            // 回填Redis
            for (ChatMemoryEntity entity : entities) {
                ChatMessageDTO dto = new ChatMessageDTO(
                        entity.getMessageType(),
                        entity.getContent(),
                        entity.getMessageOrder(),
                        System.currentTimeMillis()
                );
                redisTemplate.opsForList().rightPush(redisKey, dto);
            }
            redisTemplate.expire(redisKey, redisTtl, TimeUnit.SECONDS);

            log.info("MySQL回填{}条消息到Redis，会话[{}]", entities.size(), conversationId);

            // 返回最后N条
            List<ChatMemoryEntity> result = entities.size() > lastN
                    ? entities.subList(entities.size() - lastN, entities.size())
                    : entities;

            return result.stream()
                    .map(this::convertEntityToMessage)
                    .filter(msg -> msg != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("MySQL加载消息失败，会话[{}]", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 异步写入MySQL
     */
    @Async
    public void asyncSaveToMySQL(String conversationId, ChatMessageDTO dto) {
        try {
            ChatMemoryEntity entity = new ChatMemoryEntity(
                    null,
                    conversationId,
                    dto.getMessageType(),
                    dto.getContent(),
                    dto.getMessageOrder(),
                    null
            );
            chatMemoryMapper.insert(entity);
            log.debug("MySQL异步写入成功，会话[{}]", conversationId);
        } catch (Exception e) {
            log.error("MySQL异步写入失败，会话[{}]", conversationId, e);
        }
    }

    /**
     * Message → 类型字符串
     */
    private String getMessageType(Message message) {
        if (message instanceof UserMessage) return "USER";
        if (message instanceof AssistantMessage) return "ASSISTANT";
        if (message instanceof SystemMessage) return "SYSTEM";
        return "UNKNOWN";
    }

    /**
     * DTO → Message
     */
    private Message convertToMessage(ChatMessageDTO dto) {
        return switch (dto.getMessageType()) {
            case "USER" -> new UserMessage(dto.getContent());
            case "ASSISTANT" -> new AssistantMessage(dto.getContent());
            case "SYSTEM" -> new SystemMessage(dto.getContent());
            default -> null;
        };
    }

    /**
     * Entity → Message
     */
    private Message convertEntityToMessage(ChatMemoryEntity entity) {
        return switch (entity.getMessageType()) {
            case "USER" -> new UserMessage(entity.getContent());
            case "ASSISTANT" -> new AssistantMessage(entity.getContent());
            case "SYSTEM" -> new SystemMessage(entity.getContent());
            default -> null;
        };
    }
}