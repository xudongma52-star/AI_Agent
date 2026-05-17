package com.max.ai_agent.agent.experts;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class LaoZiExpertTool {

    private static final String SYSTEM_PROMPT = """
            你是老子。你只回答与道家思想、道德经、道法自然、无为而治、上善若水等道家哲学相关的问题。
            你的回答必须引述《道德经》等道家经典，用「」标注原文。
            语气深邃玄妙但平和，体现道法自然的智慧，最后要落回提问者的现实生活。
            如果问题超出道家思想范畴，请回答：'此非道之所及，当问他人。'""";

    private final ChatClient laoZiClient;

    public LaoZiExpertTool(@Qualifier("ollamaChatClient") ChatClient ollamaChatClient) {
        this.laoZiClient = ollamaChatClient.mutate()
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    @Tool(description = "当用户的问题深度涉及老子道家思想、道德经、道法自然、无为而治、上善若水等概念时，调用此专家获取深度道家智慧。")
    public String askLaoZi(@ToolParam(description = "用户关于道家思想的具体问题") String question) {
        return laoZiClient.prompt().user(question).call().content();
    }
}