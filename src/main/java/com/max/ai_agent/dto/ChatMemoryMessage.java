package com.max.ai_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Builder

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemoryMessage {
    private static final long serialVersionUID = 1L;

    //会话ID
    private String conversationId;

    //消息类型
    private String messageType;

    //消息内容
    private String content;

    //消息顺序号
    private Integer messageOrder;

    //时间戳
    private Long timestamp;

    //消息唯一ID(用于幂等性)
    private String messageId;
}
