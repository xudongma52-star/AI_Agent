package com.max.ai_agent.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReReadingAdvisor 重读增强 单元测试")
class ReReadingAdvisorTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private AdvisedResponse advisedResponse;

    /**
     * 构建真实的 AdvisedRequest（仅用于 userText 非空的场景）
     * 框架校验：chatModel != null 且 userText 不能为 null/空
     */
    private AdvisedRequest buildRequest(String userText) {
        return AdvisedRequest.builder()
                .chatModel(chatModel)
                .userText(userText)
                .build();
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造 - order应为0")
        void noArgs_orderShouldBe0() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            assertEquals(0, advisor.getOrder());
        }

        @Test
        @DisplayName("单参构造(template) - order应为0")
        void singleArg_orderShouldBe0() {
            ReReadingAdvisor advisor = new ReReadingAdvisor("自定义:{re2_input_query}");
            assertEquals(0, advisor.getOrder());
        }

        @Test
        @DisplayName("双参构造 - order应为指定值")
        void twoArgs_orderShouldBeSet() {
            ReReadingAdvisor advisor = new ReReadingAdvisor(ReReadingAdvisor.DEFAULT_RE2_TEMPLATE, 99);
            assertEquals(99, advisor.getOrder());
        }

        @Test
        @DisplayName("负数order - 应合法")
        void negativeOrder_shouldBeValid() {
            ReReadingAdvisor advisor = new ReReadingAdvisor(ReReadingAdvisor.DEFAULT_RE2_TEMPLATE, -1);
            assertEquals(-1, advisor.getOrder());
        }
    }

    @Nested
    @DisplayName("DEFAULT_RE2_TEMPLATE 模板验证")
    class TemplateTests {

        @Test
        @DisplayName("默认模板不应为null")
        void defaultTemplate_notNull() {
            assertNotNull(ReReadingAdvisor.DEFAULT_RE2_TEMPLATE);
        }

        @Test
        @DisplayName("默认模板应包含占位符")
        void defaultTemplate_shouldContainPlaceholder() {
            assertTrue(ReReadingAdvisor.DEFAULT_RE2_TEMPLATE.contains("{re2_input_query}"));
        }

        @Test
        @DisplayName("默认模板应包含重读提示语")
        void defaultTemplate_shouldContainReReadHint() {
            assertTrue(ReReadingAdvisor.DEFAULT_RE2_TEMPLATE.contains("Read the question again"));
        }

        @Test
        @DisplayName("默认模板应包含分隔符***")
        void defaultTemplate_shouldContainSeparator() {
            assertTrue(ReReadingAdvisor.DEFAULT_RE2_TEMPLATE.contains("***"));
        }
    }

    @Nested
    @DisplayName("before() 核心逻辑测试")
    class BeforeTests {

        // ===== null / 空 / 纯空白：用 Mock，因为 Builder 不允许构建这些值 =====

        @Test
        @DisplayName("userText为null - 应返回原始请求")
        void before_nullText_shouldReturnOriginal() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            AdvisedRequest mockRequest = mock(AdvisedRequest.class);
            when(mockRequest.userText()).thenReturn(null);

            AdvisedRequest result = advisor.before(mockRequest);

            assertSame(mockRequest, result, "userText为null时应返回原始对象");
        }

        @Test
        @DisplayName("userText为空字符串 - 应返回原始请求")
        void before_emptyText_shouldReturnOriginal() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            AdvisedRequest mockRequest = mock(AdvisedRequest.class);
            when(mockRequest.userText()).thenReturn("");

            AdvisedRequest result = advisor.before(mockRequest);

            assertSame(mockRequest, result, "userText为空时应返回原始对象");
        }

        @Test
        @DisplayName("userText为纯空白 - 应返回原始请求")
        void before_blankText_shouldReturnOriginal() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            AdvisedRequest mockRequest = mock(AdvisedRequest.class);
            when(mockRequest.userText()).thenReturn("   ");

            AdvisedRequest result = advisor.before(mockRequest);

            assertSame(mockRequest, result, "userText为纯空白时应返回原始对象");
        }

        // ===== 非空文本：用真实 Builder 构建 =====

        @Test
        @DisplayName("正常文本 - 增强后应包含原始内容和重读提示")
        void before_normalText_shouldContainOriginalAndHint() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            AdvisedRequest request = buildRequest("我今天很难过");

            AdvisedRequest result = advisor.before(request);

            assertNotNull(result);
            assertNotSame(request, result, "正常文本应返回新的增强对象");
            assertTrue(result.userText().contains("我今天很难过"), "应包含原始文本");
            assertTrue(result.userText().contains("Read the question again"), "应包含重读提示");
        }

        @Test
        @DisplayName("模板渲染 - 增强后文本应包含原始内容")
        void before_rendered_shouldContainOriginal() {
            String original = "我今天很难过";
            String template = ReReadingAdvisor.DEFAULT_RE2_TEMPLATE;
            String rendered = template.replace("{re2_input_query}", original);

            assertTrue(rendered.contains(original));
            assertTrue(rendered.contains("Read the question again"));
        }

        @Test
        @DisplayName("自定义模板 - 渲染应使用自定义内容")
        void before_customTemplate_shouldRenderCorrectly() {
            String custom = "请再读一遍: {re2_input_query} [END]";
            String userText = "测试问题";
            String expected = "请再读一遍: " + userText + " [END]";
            String rendered = custom.replace("{re2_input_query}", userText);
            assertEquals(expected, rendered);
        }

        @Test
        @DisplayName("超长文本 - 不应崩溃")
        void before_longText_shouldNotCrash() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            String longText = "我很难过".repeat(1000);
            AdvisedRequest request = buildRequest(longText);

            assertDoesNotThrow(() -> advisor.before(request));
        }

        @Test
        @DisplayName("特殊字符 - 不应崩溃")
        void before_specialChars_shouldNotCrash() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            String special = "!@#$%^&*()<script>alert('xss')</script>";
            AdvisedRequest request = buildRequest(special);

            assertDoesNotThrow(() -> advisor.before(request));
        }
    }

    @Nested
    @DisplayName("after() 透传测试")
    class AfterTests {

        @Test
        @DisplayName("after() 应原样返回响应")
        void after_shouldReturnUnchanged() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            AdvisedResponse result = advisor.after(advisedResponse);
            assertSame(advisedResponse, result);
        }

        @Test
        @DisplayName("after() 传null - 应返回null")
        void after_null_shouldReturnNull() {
            ReReadingAdvisor advisor = new ReReadingAdvisor();
            assertDoesNotThrow(() -> {
                AdvisedResponse result = advisor.after(null);
                assertNull(result);
            });
        }
    }

    @Nested
    @DisplayName("getOrder() 顺序测试")
    class OrderTests {

        @Test
        @DisplayName("默认order与MyselfLoggerAdvisor相同")
        void defaultOrder_sameAsLoggerAdvisor() {
            ReReadingAdvisor re2 = new ReReadingAdvisor();
            MyselfLoggerAdvisor logger = new MyselfLoggerAdvisor();
            assertEquals(re2.getOrder(), logger.getOrder());
        }

        @Test
        @DisplayName("order=-1 应早于 order=100 执行")
        void smallerOrder_shouldExecuteFirst() {
            ReReadingAdvisor early = new ReReadingAdvisor(ReReadingAdvisor.DEFAULT_RE2_TEMPLATE, -1);
            ReReadingAdvisor late = new ReReadingAdvisor(ReReadingAdvisor.DEFAULT_RE2_TEMPLATE, 100);

            assertTrue(early.getOrder() < late.getOrder());
        }
    }
}