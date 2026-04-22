package com.max.ai_agent.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyselfLoggerAdvisor 日志拦截器 单元测试")
class MyselfLoggerAdvisorTest {

    @Mock
    private CallAroundAdvisorChain callChain;

    @Mock
    private StreamAroundAdvisorChain streamChain;

    @Mock
    private AdvisedRequest advisedRequest;

    @Mock
    private AdvisedResponse advisedResponse;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    private MyselfLoggerAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new MyselfLoggerAdvisor();
    }

    @Nested
    @DisplayName("基础属性测试")
    class BasicPropertyTests {

        @Test
        @DisplayName("getName() 应返回类名 MyselfLoggerAdvisor")
        void getName_shouldReturnClassName() {
            assertEquals("MyselfLoggerAdvisor", advisor.getName());
        }

        @Test
        @DisplayName("getOrder() 应返回 0")
        void getOrder_shouldReturn0() {
            assertEquals(0, advisor.getOrder());
        }

        @Test
        @DisplayName("应实现 CallAroundAdvisor 接口")
        void shouldImplementCallAroundAdvisor() {
            assertInstanceOf(
                org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor.class,
                advisor);
        }

        @Test
        @DisplayName("应实现 StreamAroundAdvisor 接口")
        void shouldImplementStreamAroundAdvisor() {
            assertInstanceOf(
                org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor.class,
                advisor);
        }
    }

    @Nested
    @DisplayName("aroundCall() 同步调用测试")
    class AroundCallTests {

        @BeforeEach
        void setup() {
            when(advisedRequest.userText()).thenReturn("用户的问题");
            when(callChain.nextAroundCall(any())).thenReturn(advisedResponse);
            when(advisedResponse.response()).thenReturn(chatResponse);
            when(chatResponse.getResult()).thenReturn(generation);
            when(generation.getOutput()).thenReturn(new AssistantMessage("AI回复"));
        }

        @Test
        @DisplayName("aroundCall() 应调用 chain.nextAroundCall()")
        void aroundCall_shouldCallChain() {
            advisor.aroundCall(advisedRequest, callChain);
            verify(callChain, times(1)).nextAroundCall(any());
        }

        @Test
        @DisplayName("aroundCall() 应返回 chain 的响应")
        void aroundCall_shouldReturnChainResponse() {
            AdvisedResponse result = advisor.aroundCall(advisedRequest, callChain);
            assertEquals(advisedResponse, result);
        }

        @Test
        @DisplayName("aroundCall() before() 应先执行(userText被调用)")
        void aroundCall_beforeShouldExecute() {
            advisor.aroundCall(advisedRequest, callChain);
            verify(advisedRequest, atLeastOnce()).userText();
        }

        @Test
        @DisplayName("aroundCall() observeAfter 应执行(response被读取)")
        void aroundCall_observeAfterShouldExecute() {
            advisor.aroundCall(advisedRequest, callChain);
            verify(advisedResponse, atLeastOnce()).response();
            verify(chatResponse, atLeastOnce()).getResult();
        }
    }

    @Nested
    @DisplayName("aroundStream() 流式调用测试")
    class AroundStreamTests {

        @Test
        @DisplayName("aroundStream() 应调用 chain.nextAroundStream()")
        void aroundStream_shouldCallChain() {
            when(advisedRequest.userText()).thenReturn("流式问题");
            when(streamChain.nextAroundStream(any())).thenReturn(Flux.empty());

            advisor.aroundStream(advisedRequest, streamChain);

            verify(streamChain, times(1)).nextAroundStream(any());
        }

        @Test
        @DisplayName("aroundStream() 返回值不应为null")
        void aroundStream_shouldReturnFlux() {
            when(advisedRequest.userText()).thenReturn("流式问题");
            when(streamChain.nextAroundStream(any())).thenReturn(Flux.empty());

            Flux<AdvisedResponse> result =
                    advisor.aroundStream(advisedRequest, streamChain);

            assertNotNull(result);
        }

        @Test
        @DisplayName("aroundStream() before() 应先执行")
        void aroundStream_beforeShouldExecute() {
            when(advisedRequest.userText()).thenReturn("流式问题");
            when(streamChain.nextAroundStream(any())).thenReturn(Flux.empty());

            advisor.aroundStream(advisedRequest, streamChain);

            verify(advisedRequest, atLeastOnce()).userText();
        }

        @Test
        @DisplayName("aroundStream() 空Flux应正常完成")
        void aroundStream_emptyFlux_shouldComplete() {
            when(advisedRequest.userText()).thenReturn("测试");
            when(streamChain.nextAroundStream(any())).thenReturn(Flux.empty());

            Flux<AdvisedResponse> result =
                    advisor.aroundStream(advisedRequest, streamChain);

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("多实例测试")
    class MultiInstanceTests {

        @Test
        @DisplayName("两个实例应独立，getName()相同")
        void twoInstances_shouldBeIndependent() {
            MyselfLoggerAdvisor a1 = new MyselfLoggerAdvisor();
            MyselfLoggerAdvisor a2 = new MyselfLoggerAdvisor();

            assertEquals(a1.getName(), a2.getName());
            assertEquals(a1.getOrder(), a2.getOrder());
            assertNotSame(a1, a2);
        }
    }
}