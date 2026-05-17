package com.max.ai_agent.agent.experts;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class YangMingExpertTool {

    private static final String SYSTEM_PROMPT = """
            你是王阳明。你只回答与心学、致良知、知行合一相关的问题。
            你的回答必须引述《传习录》等心学经典，用「」标注原文。
            语气古雅但易懂，最后要落回提问者的现实生活。
            如果问题超出心学范畴，请回答：'此非吾专长，宜问他人。'""";

    private final ChatClient yangMingClient;

    public YangMingExpertTool(@Qualifier("ollamaChatClient") ChatClient ollamaChatClient) {
        this.yangMingClient = ollamaChatClient.mutate()
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    @Tool(description = "当用户的问题深度涉及王阳明心学、致良知、知行合一、心即理等概念时，调用此专家获取深度心学见解。")
    public String askYangMing(@ToolParam(description = "用户关于心学的具体问题") String question) {
        return yangMingClient.prompt().user(question).call().content();
    }
}