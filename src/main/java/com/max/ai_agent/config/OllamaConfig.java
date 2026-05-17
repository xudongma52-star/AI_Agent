package com.max.ai_agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class OllamaConfig {

    /**
     * 主 ChatClient.Builder，使用 DashScope 作为默认模型。
     * 覆盖自动配置的 ChatClient.Builder，解决多 ChatModel Bean 歧义问题。
     */
    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * Ollama 本地模型的 ChatClient，供专家 Agent 使用。
     */
    @Bean
    public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
