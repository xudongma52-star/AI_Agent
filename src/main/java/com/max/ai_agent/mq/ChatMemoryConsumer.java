// mq/ChatMemoryConsumer.java
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
        // 并发消费者数量（顺序性要求高的场景设为1）
        concurrency = "1"
        // concurrency="1"：单线程消费，保证消息顺序
        // 如果不在乎顺序，可以设为"3-5"提高吞吐量,因为我们这个项目是存聊天对话的，要保证聊天顺序，不然ai在读消息会因为顺序问题照成错误回答
    )
    public void consumeChatMemory(ChatMemoryMessage mqMessage,
                                   Message amqpMessage,
                                   Channel channel) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        String messageId = mqMessage.getMessageId();

        log.info("收到消息，messageId：{}，会话[{}]", messageId, mqMessage.getConversationId());

        try {
            // ===== 幂等性检查 =====
            // 如果消费失败重试，可能重复消费同一条消息
            // 用message_order做唯一约束，或者用messageId去重
            saveToMySQL(mqMessage);

            // ===== 手动ACK =====
            // 告诉RabbitMQ：这条消息我处理成功了，可以删除
            channel.basicAck(deliveryTag, false);
            log.info("消息消费成功，messageId：{}", messageId);

        } catch (DuplicateKeyException e) {
            // 重复消息（幂等），直接ACK，不重试
            log.warn("重复消息，忽略，messageId：{}", messageId);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("消息消费失败，messageId：{}，原因：{}", messageId, e.getMessage(), e);

            // 判断是否还有重试次数
            Integer retryCount = (Integer) amqpMessage.getMessageProperties()
                    .getHeaders().getOrDefault("x-retry-count", 0);

            if (retryCount < 3) {
                // 还有重试次数：NACK，放回队列重试
                // requeue=false：不放回原队列，走死信队列
                // requeue=true：放回原队列头部
                channel.basicNack(deliveryTag, false, true);
                log.warn("消息消费失败，放回队列重试，第{}次，messageId：{}", retryCount + 1, messageId);
            } else {
                // 超过重试次数：NACK不重新入队，走死信队列
                channel.basicNack(deliveryTag, false, false);
                log.error("消息超过重试次数，进入死信队列，messageId：{}", messageId);
            }
        }
    }

    /**
     * 死信队列消费者（告警/人工处理）
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_DEAD_QUEUE)
    public void consumeDeadLetter(ChatMemoryMessage mqMessage,
                                   Message amqpMessage,
                                   Channel channel) throws IOException {
        log.error("【死信告警】消息持久化失败需人工处理！" +
                "messageId：{}，会话[{}]，内容：{}",
                mqMessage.getMessageId(),
                mqMessage.getConversationId(),
                mqMessage.getContent());

        // TODO: 发钉钉/企微告警
        // TODO: 写入告警表，人工补偿

        // 告警处理完毕，ACK
        channel.basicAck(amqpMessage.getMessageProperties().getDeliveryTag(), false);
    }

    /**
     * 保存到MySQL
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
        log.debug("MySQL写入成功，会话[{}]，顺序：{}",
                mqMessage.getConversationId(), mqMessage.getMessageOrder());
    }
}