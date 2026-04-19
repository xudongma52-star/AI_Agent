// dto/ChatMessageDTO.java
package com.max.ai_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO implements Serializable {

    private String messageType;   // USER, ASSISTANT, SYSTEM
    private String content;
    private Integer messageOrder;
    private Long timestamp;
}