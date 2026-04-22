package com.max.ai_agent.mq;

import com.max.ai_agent.config.RabbitMQConfig;
import com.max.ai_agent.dto.ChatMemoryMessage;
import com.max.ai_agent.entity.ChatMemoryEntity;
import com.max.ai_agent.mapper.ChatMemoryMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryConsumer {

    private final ChatMemoryMapper chatMemoryMapper;

    @RabbitListener(
            queues = RabbitMQConfig.CHAT_MEMORY_QUEUE,
            concurrency = "1"
    )
    public void consumeChatMemory(ChatMemoryMessage mqMessage,
                                  Message amqpMessage,
                                  Channel channel) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        String messageId = mqMessage.getMessageId();

        log.info("收到消息，messageId：{}，会话[{}]", messageId, mqMessage.getConversationId());

        try {
            // 1. 尝试保存到数据库
            saveToMySQL(mqMessage);

            // 2. 成功：手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("消息消费成功，messageId：{}", messageId);

        } catch (DuplicateKeyException e) {
            // 3. 幂等性命中：重复消息，直接 ACK 吞掉，绝对不能往外抛！
            log.warn("重复消息，忽略，messageId：{}", messageId);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            // 4. 业务异常：直接往外抛！交给 Spring Retry 接管
            log.error("消息消费异常，messageId：{}，原因：{}", messageId, e.getMessage());
            // ⚠️ 注意：这里不再手动 Nack，而是抛出异常！
            throw new RuntimeException("消息消费失败，等待Spring重试", e);
        }
    }

    /**
     * 死信队列消费者（最终兜底）
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_DEAD_QUEUE)
    public void consumeDeadLetter(ChatMemoryMessage mqMessage,
                                  Message amqpMessage,
                                  Channel channel) throws IOException {
        log.error("【死信告警】消息经过多次重试仍失败，需人工处理！ messageId：{}，会话[{}]，内容：{}",
                mqMessage.getMessageId(),
                mqMessage.getConversationId(),
                mqMessage.getContent());

        // TODO: 发钉钉/企微告警
        // TODO: 写入告警表，人工补偿

        // 确认收到死信
        channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
    }

    /**
     * 保存到 MySQL
     */
    private void saveToMySQL(ChatMemoryMessage mqMessage) {
        ChatMemoryEntity entity = new ChatMemoryEntity(
                null,
                mqMessage.getConversationId(),
                mqMessage.getMessageType(),
                mqMessage.getContent(),
                mqMessage.getMessageOrder(),
                null
        );
        chatMemoryMapper.insert(entity);
    }
}