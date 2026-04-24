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
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisMySqlChatMemory implements ChatMemory {

    private static final String REDIS_KEY_PREFIX = "chat:memory:";
    private static final String COUNTER_KEY_PREFIX = "chat:counter:";
    private static final String EMPTY_KEY_PREFIX = "chat:empty:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatMemoryMapper chatMemoryMapper;
    private final RabbitTemplate rabbitTemplate;
    private final DefaultRedisScript<Long> addMessageScript;
    private final ObjectMapper objectMapper;

    @Value("${chat.memory.redis-ttl:3600}")
    private long redisFirTtl;

    @Value("${chat.memory.max-size:100}")
    private int maxSize;

    private long redisTtl() {
        long jitter = ThreadLocalRandom.current().nextLong(0, 300); // 0~5分钟随机
        return redisFirTtl + jitter;
    }

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

        // 新增消息了，空标记应该失效
        redisTemplate.delete(EMPTY_KEY_PREFIX + conversationId);

        Integer mysqlMaxOrder = chatMemoryMapper.selectMaxOrder(conversationId);
        int maxOrder = mysqlMaxOrder == null ? 0 : mysqlMaxOrder;

        long ttl = redisTtl();
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
                    String.valueOf(ttl),
                    String.valueOf(maxOrder)
            );

            if (order == null) {
                log.error("Lua脚本执行失败，会话[{}]", conversationId);
                throw new RuntimeException("Redis写入失败");
            }
            log.debug("Lua脚本执行成功，顺序号:{}, 会话[{}]", order, conversationId);


            redisMsg.setMessageOrder(order.intValue());

            //如果持久化失败就全部回滚，但是无法实现强一致性,
            // 假设同一个会话，用户手速极快，瞬间发了两条消息（线程A 和 线程B）：
            // 1. 线程A 写入 Redis: [msg1, msgA]
            //2. 线程B 写入 Redis: [msg1, msgA, msgB]
            //3. 线程A 发送 MQ 失败！进入 catch
            //4. 线程A 执行 rightPop: [msg1, msgA]  ← 把 msgB 给删掉了！
            //5. 线程B 发送 MQ 成功。
            //结果：线程B 的消息在 Redis 里被线程A 误删了！
            //而 MQ 里有 msgB，Redis 里没有，数据彻底乱了。
            //所以：容忍短暂丢失，追求最终一致性。
//            try{
//                rabbitTemplate.convertAndSend(
//                        RabbitMQConfig.CHAT_EXCHANGE,
//                        RabbitMQConfig.CHAT_ROUTING_KEY,
//                        redisMsg
//                );
//            }catch (Exception mqEX){
//                log.error("MQ发送失败，回滚Redis,会话[{}]",conversationId, mqEX);
//                //删除redis的这句对话，实现回滚
//                redisTemplate.opsForList().rightPop(redisKey);
//                throw new RuntimeException("消息持久化失败",mqEX);
            //一旦 RabbitMQ 宕机、或者网络抖动导致发送超时，用户的聊天接口直接报错（返回 500）导致故障蔓延。
//            }

            //为了高性能！！！
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.CHAT_EXCHANGE,
                        RabbitMQConfig.CHAT_ROUTING_KEY,
                        redisMsg
                );
                log.debug("消息发送到MQ成功, messageId:{}, 会话[{}]", messageId, conversationId);
            } catch (Exception mqEx) {
                // 只记录错误日志，绝对不要 rightPop，也绝对不要 throw 异常！
                log.error("MQ发送失败，消息仅存于Redis，等待兜底补偿。messageId:{}, 会话[{}]",
                        messageId, conversationId, mqEx);
            }


            log.debug("消息发送到MQ成功, messageId:{}, 会话[{}]", messageId, conversationId);
        }

        log.debug("写入{}条消息，会话[{}]", messages.size(), conversationId);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {



        String redisKey = REDIS_KEY_PREFIX + conversationId;


        // 检查空标记双重检查缓存穿透
        try {
            Object emptyMark = redisTemplate.opsForValue().get(EMPTY_KEY_PREFIX + conversationId);
            if ("EMPTY".equals(emptyMark)) {
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("Redis读取空标记异常，会话[{}]", conversationId, e);
            // 异常继续往下走，不要因为空标记查询失败阻断业务
        }

        Long redisSize = null;
        try {
            redisSize = redisTemplate.opsForList().size(redisKey);
            //锁外，快速通道为了性能，在redis能查到
            if (redisSize != null && redisSize > 0) {
                log.debug("Redis命中，会话[{}] 共{}条", conversationId, redisSize);
                return getFromRedis(redisKey, lastN);
            }
        } catch (Exception e) {
            log.warn("Redis异常，降级到MySql,会话[{}]",conversationId, e);
            //降级到MySql
        }


        //分布式锁，防缓存击穿，保护MySQL
        String lockKey = "chat:lock:" + conversationId;
        boolean locked = false;
        try{
            //安全写法：即使返回 null，结果也只是 false
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey,"1",10, TimeUnit.SECONDS));
            //再检查一下缓存
            if(locked){
                redisSize = redisTemplate.opsForList().size(redisKey);
                if(redisSize != null && redisSize > 0){
                    return getFromRedis(redisKey, lastN);
                }
                return loadFromMySQL(conversationId,lastN,redisKey);
            }else{
                //没拿到的话，让他睡一会，等会再尝试
                Thread.sleep(200);
                redisSize = redisTemplate.opsForList().size(redisKey);
                if (redisSize != null && redisSize > 0) {
                    return getFromRedis(redisKey, lastN);
                }
                return loadFromMySQL(conversationId, lastN, redisKey);
                //return loadFromMySQL(conversationId,lastN,redisKey);//1000个请求，如果第一个获取锁之后，过了200ms，999个请求又去查询MySql导致MySql又挂了
            }
        } catch (InterruptedException e) {
            //可以让上层调用者知道这里发生过中断
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }finally {
            if(locked){
                redisTemplate.delete(lockKey);
            }
        }

    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(REDIS_KEY_PREFIX + conversationId);
        redisTemplate.delete(COUNTER_KEY_PREFIX + conversationId);
        redisTemplate.delete(EMPTY_KEY_PREFIX + conversationId);

        //先清除MySql
        chatMemoryMapper.delete(
                new LambdaQueryWrapper<ChatMemoryEntity>()
                        .eq(ChatMemoryEntity::getConversationId, conversationId)
        );
        log.info("已清除会话[{}]所有记忆", conversationId);

        //再清除Redis
        redisTemplate.delete(REDIS_KEY_PREFIX + conversationId);
        redisTemplate.delete(COUNTER_KEY_PREFIX + conversationId);

        //写入空标记，防止穿透
        redisTemplate.opsForValue().set(EMPTY_KEY_PREFIX + conversationId,"EMPTY",5, TimeUnit.MINUTES);
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
            //解决缓存穿透
            if(entities.isEmpty()){
                //缓存空标记
                redisTemplate.opsForValue().set(EMPTY_KEY_PREFIX + conversationId,"EMPTY",5,TimeUnit.MINUTES);
                return Collections.emptyList();
            }


            Collections.reverse(entities);

            String counterKey = COUNTER_KEY_PREFIX + conversationId;


            redisTemplate.executePipelined((RedisCallback<Object>)connection ->{
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
                        // 修复1：显式指定 UTF-8 编码，防止不同环境默认编码不一致导致乱码
                        byte[] keyBytes = redisKey.getBytes(StandardCharsets.UTF_8);
                        byte[] valueBytes = json.getBytes(StandardCharsets.UTF_8);
                        connection.listCommands().rPush(redisKey.getBytes(),json.getBytes());
                    } catch (JsonProcessingException e) {
                        log.warn("回填Redis序列化失败", e);
                    }
                }
                return null;
            });

            redisTemplate.expire(redisKey, redisTtl(), TimeUnit.SECONDS);

            Integer maxOrder = chatMemoryMapper.selectMaxOrder(conversationId);
            if (maxOrder != null && maxOrder > 0) {
                redisTemplate.opsForValue().set(counterKey, maxOrder);
                redisTemplate.expire(counterKey, redisTtl(), TimeUnit.SECONDS);
            }
            redisTemplate.delete(EMPTY_KEY_PREFIX + conversationId);
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