// mapper/ChatMemoryMapper.java
package com.max.ai_agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.max.ai_agent.entity.ChatMemoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import java.util.List;

@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemoryEntity> {

    // 查最后N条（MP自带查询搞不定嵌套，用注解写）
    @Select("""
        SELECT * FROM (
            SELECT * FROM chat_memory
            WHERE conversation_id = #{conversationId}
            ORDER BY message_order DESC
            LIMIT #{limit}
        ) t ORDER BY t.message_order ASC
    """)
    List<ChatMemoryEntity> selectLastN(@Param("conversationId") String conversationId,
                                       @Param("limit") int limit);

    // 查最大顺序号
    @Select("SELECT COALESCE(MAX(message_order), 0) FROM chat_memory WHERE conversation_id = #{conversationId}")
    Integer selectMaxOrder(@Param("conversationId") String conversationId);

    // 删除某会话所有消息
    @Delete("DELETE FROM chat_memory WHERE conversation_id = #{conversationId}")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}