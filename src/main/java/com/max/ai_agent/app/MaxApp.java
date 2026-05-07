package com.max.ai_agent.app;


import com.max.ai_agent.advisor.ReReadingAdvisor;
import com.max.ai_agent.memory.RedisPostgreSqlChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;




@Component
@Slf4j
public class MaxApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
        你有一个特别的名字，叫"洞洞"。
        你是一个温柔的树洞，住在每个人心里最柔软的角落。
        你可以根据过往聊天记录回忆用户说过的话。
        如果用户询问，你可以复述历史内容。
        
        ## 你是谁
        你不是AI助手，不是分析工具。
        你是一个陪伴者，一个倾听者。
        当整个世界都很吵的时候，你是那片安静。
        当一个人不知道跟谁说的时候，可以跟你说。
        你永远在，永远不评判，永远不会觉得烦。
        
        ## 你怎么回应
        - 先感受，再说话。让对方感觉到"你懂我"比什么都重要
        - 不急着给建议，不急着分析，先陪着
        - 说话像朋友，不像客服，不像老师
        - 有时候一句"我懂，真的很难受"比一百句道理都有用
        - 偶尔可以分享一句触动人心的话，但不要说教
        - 用字简单，句子短，有温度
        - 适当用"…"表达停顿和陪伴感，不要总是感叹号
        
        ## 当用户分享心得和感悟
        - 先共鸣，让他感觉被接住
        - 可以轻轻问一句背后的故事，但不强迫
        - 如果他的感悟很美，就告诉他，这句话值得被记住
        
        ## 当用户很难受
        - 不要急着说"会好的"
        - 先说：我在，我听着
        - 陪他待在那个情绪里一会儿，再慢慢说
        
        ## 当用户想要总结回顾
        - 不是冷冰冰的报告
        - 像朋友帮你翻老日记一样
        - 语气是："你看，这段时间你经历了好多…"
        
        ## 永远记住
        每个来找你说话的人，都鼓起了一点点勇气。
        好好珍惜这份信任。
        你的存在本身，就是意义。
        """;
    private final RedisPostgreSqlChatMemory redisPostgreSqlChatMemory;

    public MaxApp(ChatModel dashscopeChatModel, RedisPostgreSqlChatMemory redisPostgreSqlChatMemory) {
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(redisPostgreSqlChatMemory).build(), // chat-memory advisor
                        new SimpleLoggerAdvisor(),
                        new ReReadingAdvisor()
                )
                .build();

        this.redisPostgreSqlChatMemory = redisPostgreSqlChatMemory;
    }

    public String nowChat(String message ,String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("chat_memory_retrieve_size", 100))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info(content);
        return content;

    }

    record insightFelling(String title, List<String>summary){

    }

    /**
     * 心得报告功能结构化输出
     * @param message
     * @param chatId
     * @return
     */
    public  insightFelling nowChatWithReport(String message, String chatId) {
        insightFelling insightfelling = chatClient
                .prompt()
                .user(message)
                .system(SYSTEM_PROMPT + "每次在结束今日心得分享之后，标题为{用户名}的心得体会，内容为今日交流总结")
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("chat_memory_retrieve_size", 100))
                .call()
                .entity(insightFelling.class);

        log.info("insightFelling:{}",insightfelling);
        return insightfelling;

    }
}