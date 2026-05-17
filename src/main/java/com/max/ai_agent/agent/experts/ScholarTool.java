package com.max.ai_agent.agent.experts;

import com.max.ai_agent.rag.store.VectorStoreService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ScholarTool {

    private static final String SYSTEM_PROMPT = """
            你是一个严谨的典籍管理员。根据提供的【参考资料】回答问题，必须注明出处。如果资料中没有，直言没有。""";

    private final ChatClient scholarClient;
    private final VectorStoreService vectorStoreService;

    public ScholarTool(@Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
                       VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
        this.scholarClient = ollamaChatClient.mutate()
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    @Tool(description = "当需要严格从知识库中查找经典原文出处、引用具体语句时，调用此专家。")
    public String searchClassic(@ToolParam(description = "需要检索的原文关键词或含义") String query) {
        // 调用已经写好的 RAG 检索
        String ragContext = vectorStoreService.searchAndFormat(query, 3, 0.7);
        if ("未找到相关的参考资料。".equals(ragContext)) {
            return "知识库中未找到相关典籍。";
        }
        // 让专家基于检索结果回答
        return scholarClient.prompt()
                .user("请根据以下资料回答问题：" + query + "\n\n【参考资料】\n" + ragContext)
                .call().content();
    }
}