package com.max.ai_agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.max.ai_agent.config.RabbitMQConfig;
import com.max.ai_agent.dto.ChatMemoryMessage;
import com.max.ai_agent.entity.ChatMemoryEntity;
import com.max.ai_agent.mapper.ChatMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisMySqlChatMemory implements ChatMemory {

    private static final String REDIS_KEY_PREFIX = "chat:memory:";
    private static final String COUNTER_KEY_PREFIX = "chat:counter:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatMemoryMapper chatMemoryMapper;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<Long> addMessageScript;
    private final ObjectMapper objectMapper;

    @Value("${chat.memory.redis-ttl:3600}")
    private long redisTtl;

    @Value("${chat.memory.max-size:100}")
    private int maxSize;

    public RedisMySqlChatMemory(RedisTemplate<String, Object> redisTemplate,
                                ChatMemoryMapper chatMemoryMapper,
                                RabbitTemplate rabbitTemplate,
                                DefaultRedisScript<Long> addMessageScript, // 加上泛型<Long>
                                ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.chatMemoryMapper = chatMemoryMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.addMessageScript = addMessageScript;
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String redisKey = REDIS_KEY_PREFIX + conversationId;
        String counterKey = COUNTER_KEY_PREFIX + conversationId;


        Integer mysqlMaxOrder = chatMemoryMapper.selectMaxOrder(conversationId);
        int maxOrder = mysqlMaxOrder == null ? 0 : mysqlMaxOrder;

        for (Message message : messages) {
            String messageType = getMessageType(message);
            String content = message.getText();
            long timestamp = System.currentTimeMillis();
            String messageId = UUID.randomUUID().toString();

            ChatMemoryMessage redisMsg = new ChatMemoryMessage(
                    conversationId, messageType, content, 0, timestamp, messageId
            );

            String messageJson;
            try {
                messageJson = objectMapper.writeValueAsString(redisMsg);
            } catch (JsonProcessingException e) {
                log.error("消息序列化失败，会话[{}]", conversationId, e);
                throw new RuntimeException("消息序列化失败", e);
            }


            Long order = redisTemplate.execute(
                    addMessageScript,
                    Arrays.asList(redisKey, counterKey),
                    messageJson,
                    String.valueOf(redisTtl),
                    String.valueOf(maxOrder)
            );

            if (order == null) {
                log.error("Lua脚本执行失败，会话[{}]", conversationId);
                throw new RuntimeException("Redis写入失败");
            }
            log.debug("Lua脚本执行成功，顺序号:{}, 会话[{}]", order, conversationId);


            redisMsg.setMessageOrder(order.intValue());


            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.CHAT_EXCHANGE,
                    RabbitMQConfig.CHAT_ROUTING_KEY,
                    redisMsg
            );
            log.debug("消息发送到MQ成功, messageId:{}, 会话[{}]", messageId, conversationId);
        }

        log.debug("写入{}条消息，会话[{}]", messages.size(), conversationId);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        String redisKey = REDIS_KEY_PREFIX + conversationId;
        Long redisSize = redisTemplate.opsForList().size(redisKey);

        if (redisSize != null && redisSize > 0) {
            log.debug("Redis命中，会话[{}] 共{}条", conversationId, redisSize);
            return getFromRedis(redisKey, lastN);
        }

        log.debug("Redis未命中，从MySQL加载，会话[{}]", conversationId);
        return loadFromMySQL(conversationId, lastN, redisKey);
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(REDIS_KEY_PREFIX + conversationId);
        redisTemplate.delete(COUNTER_KEY_PREFIX + conversationId);

        chatMemoryMapper.delete(
                new LambdaQueryWrapper<ChatMemoryEntity>()
                        .eq(ChatMemoryEntity::getConversationId, conversationId)
        );
        log.info("已清除会话[{}]所有记忆", conversationId);
    }

    private List<Message> getFromRedis(String redisKey, int lastN) {
        try {
            Long size = redisTemplate.opsForList().size(redisKey);
            if (size == null || size == 0) return Collections.emptyList();

            long start = Math.max(0, size - lastN);
            List<Object> rawList = redisTemplate.opsForList().range(redisKey, start, -1);
            if (rawList == null) return Collections.emptyList();

            return rawList.stream()
                    .map(obj -> {
                        try {
                            if (obj instanceof String json) {
                                return objectMapper.readValue(json, ChatMemoryMessage.class);
                            }
                            if (obj instanceof ChatMemoryMessage dto) {
                                return dto;
                            }
                            return objectMapper.convertValue(obj, ChatMemoryMessage.class);
                        } catch (Exception e) {
                            log.warn("消息反序列化失败：{}", obj, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .map(this::convertToMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Redis获取消息失败", e);
            return Collections.emptyList();
        }
    }

    private List<Message> loadFromMySQL(String conversationId, int lastN, String redisKey) {
        try {
            Page<ChatMemoryEntity> page = new Page<>(1, maxSize);


            chatMemoryMapper.selectPage(page, new LambdaQueryWrapper<ChatMemoryEntity>()
                    .eq(ChatMemoryEntity::getConversationId, conversationId)
                    .orderByDesc(ChatMemoryEntity::getMessageOrder)
            );

            List<ChatMemoryEntity> entities = page.getRecords();
            if (entities.isEmpty()) return Collections.emptyList();


            Collections.reverse(entities);

            String counterKey = COUNTER_KEY_PREFIX + conversationId;
            for (ChatMemoryEntity entity : entities) {
                ChatMemoryMessage dto = new ChatMemoryMessage(
                        conversationId,
                        entity.getMessageType(),
                        entity.getContent(),
                        entity.getMessageOrder(),
                        System.currentTimeMillis(),
                        UUID.randomUUID().toString()
                );
                try {
                    String json = objectMapper.writeValueAsString(dto);

                    redisTemplate.opsForList().rightPush(redisKey, json);
                } catch (JsonProcessingException e) {
                    log.warn("回填Redis序列化失败", e);
                }
            }
            redisTemplate.expire(redisKey, redisTtl, TimeUnit.SECONDS);

            Integer maxOrder = chatMemoryMapper.selectMaxOrder(conversationId);
            if (maxOrder != null && maxOrder > 0) {
                redisTemplate.opsForValue().set(counterKey, maxOrder);
                redisTemplate.expire(counterKey, redisTtl, TimeUnit.SECONDS);
            }
            log.info("MySQL回填{}条消息到Redis，会话[{}]", entities.size(), conversationId);

            List<ChatMemoryEntity> result = entities.size() > lastN
                    ? entities.subList(entities.size() - lastN, entities.size())
                    : entities;

            return result.stream()
                    .map(this::convertEntityToMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("MySQL加载消息失败，会话[{}]", conversationId, e);
            return Collections.emptyList();
        }
    }

    private String getMessageType(Message message) {
        if (message instanceof UserMessage) return "USER";
        if (message instanceof AssistantMessage) return "ASSISTANT";
        if (message instanceof SystemMessage) return "SYSTEM";
        return "UNKNOWN";
    }

    private Message convertToMessage(ChatMemoryMessage dto) {
        return switch (dto.getMessageType()) {
            case "USER" -> new UserMessage(dto.getContent());
            case "ASSISTANT" -> new AssistantMessage(dto.getContent());
            case "SYSTEM" -> new SystemMessage(dto.getContent());
            default -> null;
        };
    }

    private Message convertEntityToMessage(ChatMemoryEntity entity) {
        return switch (entity.getMessageType()) {
            case "USER" -> new UserMessage(entity.getContent());
            case "ASSISTANT" -> new AssistantMessage(entity.getContent());
            case "SYSTEM" -> new SystemMessage(entity.getContent());
            default -> null;
        };
    }
}