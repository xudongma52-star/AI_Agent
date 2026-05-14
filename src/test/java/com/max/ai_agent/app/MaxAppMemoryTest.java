package com.max.ai_agent.app;

import com.max.ai_agent.memory.RedisPostgreSqlChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import java.util.List;

@Slf4j
@SpringBootTest
class MaxAppMemoryTest {

    @Resource
    private MaxApp maxApp;

    @Resource
    private RedisPostgreSqlChatMemory chatMemory;

    @Test
    void testMemoryStored() {

        String chatId = "test-memory-004";

        String reply = maxApp.nowChat("我喜欢学Java", chatId);
        log.debug(reply);


        String reply1 = maxApp.nowChat("我最喜学什么", chatId);
        log.debug(reply1);



    }


}