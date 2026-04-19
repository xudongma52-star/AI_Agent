package com.max.ai_agent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class MaxAppTest {

    @Resource
    private MaxApp maxApp;
    @Test
    void nowChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "今天其实过得有点恍惚。\n" +
                "\n" +
                "早上起来的时候脑子还没完全转过来，就开始想一些有的没的——比如我到底在忙什么，忙了这么久，有没有真的往前走一步。有时候觉得自己很努力，但努力和进步之间好像隔着一层什么，看不清楚。\n" +
                "\n" +
                "跟人说话的时候会好一点。不是因为说了什么有意义的话，就是那种\"有人在\"的感觉，会让人踏实一些。我发现我其实挺需要这个的，虽然平时不太承认。\n" +
                "\n" +
                "今天让我印象深的是一个很小的瞬间。窗外的光突然变了，那种下午四五点钟特有的斜光，打在墙上，有点暖，有点静。就那么几秒钟，什么都不想，只是看着。后来又回到原来的状态了，但那几秒钟是真的舒服。\n" +
                "\n" +
                "心得的话，我觉得今天提醒了我一件事——不要总是等一个\"准备好了\"的时刻。很多时候就是硬着头皮去做，做着做着就顺了。道理都懂，但每次还是要重新学一遍，也挺有意思的。\n" +
                "\n" +
                "就这样吧。也没什么大感悟，平常的一天，平常地过完了。";
        maxApp.nowChatWithReport(message,chatId);
    }
}