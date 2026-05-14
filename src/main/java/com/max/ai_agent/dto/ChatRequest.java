package com.max.ai_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequest {
    /**
     * 用户消息
     */
    private String message;

    /**
     * 会话ID（start时可为空，continue时必填）
     */
    private String chatId;

    /**
     * 是否启用知识库检索（默认false）
     */
    private Boolean useRag = false;
}
