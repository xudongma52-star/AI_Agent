package com.max.ai_agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data                    // Lombok：自动生成getter/setter/toString/equals/hashCode
@NoArgsConstructor       // Lombok：生成无参构造
@AllArgsConstructor
@Builder
@TableName("chat_memory") // MP：告诉MP这个类对应数据库哪张表
public class ChatMemoryEntity {

    @TableId(type = IdType.AUTO)  // MP：主键，AUTO=数据库自增
    private Long id;

    @TableField("conversation_id") // MP：对应数据库字段名
    private String conversationId; // 会话ID，同一个用户一次聊天的唯一标识

    @TableField("message_type")
    private String messageType;    // 消息类型：USER用户说的/ASSISTANT AI说的/SYSTEM系统提示

    @TableField("content")
    private String content;        // 消息内容

    @TableField("message_order")
    private Integer messageOrder;  // 消息顺序号，保证取出来的消息是按顺序的

    @TableField(value = "created_time", fill = FieldFill.INSERT) // 插入时自动填充
    private LocalDateTime createdTime;
}