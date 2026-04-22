package com.max.ai_agent.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaxApp 应用层 单元测试")
class MaxAppTest {

    @Nested
    @DisplayName("SYSTEM_PROMPT 内容验证")
    class SystemPromptTests {

        @Test
        @DisplayName("SYSTEM_PROMPT - 应包含角色名'洞洞'")
        void systemPrompt_shouldContainRoleName() {
            String prompt = getSystemPrompt();
            assertTrue(prompt.contains("洞洞"));
        }

        @Test
        @DisplayName("SYSTEM_PROMPT - 应包含'你是谁'章节")
        void systemPrompt_shouldContainWhoYouAre() {
            assertTrue(getSystemPrompt().contains("你是谁"));
        }

        @Test
        @DisplayName("SYSTEM_PROMPT - 应包含'你怎么回应'章节")
        void systemPrompt_shouldContainHowToRespond() {
            assertTrue(getSystemPrompt().contains("你怎么回应"));
        }

        @Test
        @DisplayName("SYSTEM_PROMPT - 应包含'永远记住'章节")
        void systemPrompt_shouldContainRemember() {
            assertTrue(getSystemPrompt().contains("永远记住"));
        }

        @Test
        @DisplayName("SYSTEM_PROMPT - 不应为null或空")
        void systemPrompt_shouldNotBeNullOrEmpty() {
            String prompt = getSystemPrompt();
            assertNotNull(prompt);
            assertFalse(prompt.isBlank());
        }

        @Test
        @DisplayName("SYSTEM_PROMPT - 长度应在合理范围(100~5000字符)")
        void systemPrompt_lengthShouldBeReasonable() {
            String prompt = getSystemPrompt();
            assertTrue(prompt.length() > 100, "提示词过短");
            assertTrue(prompt.length() < 5000, "提示词过长会浪费Token");
        }

        private String getSystemPrompt() {
            try {
                var field = MaxApp.class.getDeclaredField("SYSTEM_PROMPT");
                field.setAccessible(true);
                return (String) field.get(null);
            } catch (Exception e) {
                fail("无法获取SYSTEM_PROMPT: " + e.getMessage());
                return null;
            }
        }
    }

    @Nested
    @DisplayName("insightFelling Record 测试")
    class InsightFellingTests {

        @Test
        @DisplayName("insightFelling - Record类应存在")
        void insightFelling_classShouldExist() throws Exception {
            Class<?> clazz = getInsightFellingClass();
            assertNotNull(clazz);
        }

        @Test
        @DisplayName("insightFelling - 应有2个字段(title, summary)")
        void insightFelling_shouldHave2Components() throws Exception {
            var components = getInsightFellingClass().getRecordComponents();
            assertNotNull(components);
            assertEquals(2, components.length);
        }

        @Test
        @DisplayName("insightFelling - 第一个字段名应为title")
        void insightFelling_firstFieldShouldBeTitle() throws Exception {
            var components = getInsightFellingClass().getRecordComponents();
            assertEquals("title", components[0].getName());
        }

        @Test
        @DisplayName("insightFelling - 第二个字段名应为summary")
        void insightFelling_secondFieldShouldBeSummary() throws Exception {
            var components = getInsightFellingClass().getRecordComponents();
            assertEquals("summary", components[1].getName());
        }

        @Test
        @DisplayName("insightFelling - title应为String类型")
        void insightFelling_titleShouldBeString() throws Exception {
            var components = getInsightFellingClass().getRecordComponents();
            assertEquals(String.class, components[0].getType());
        }

        @Test
        @DisplayName("insightFelling - summary应为List类型")
        void insightFelling_summaryShouldBeList() throws Exception {
            var components = getInsightFellingClass().getRecordComponents();
            assertEquals(List.class, components[1].getType());
        }

        private Class<?> getInsightFellingClass() throws ClassNotFoundException {
            return Class.forName("com.max.ai_agent.app.MaxApp$insightFelling");
        }
    }

    @Nested
    @DisplayName("方法签名验证")
    class MethodSignatureTests {

        @Test
        @DisplayName("nowChat() - 应有2个String参数")
        void nowChat_shouldHave2Params() throws Exception {
            var method = MaxApp.class.getDeclaredMethod(
                    "nowChat", String.class, String.class);
            assertEquals(2, method.getParameterCount());
        }

        @Test
        @DisplayName("nowChat() - 返回类型应为String")
        void nowChat_returnTypeShouldBeString() throws Exception {
            var method = MaxApp.class.getDeclaredMethod(
                    "nowChat", String.class, String.class);
            assertEquals(String.class, method.getReturnType());
        }

        @Test
        @DisplayName("nowChatWithReport() - 应有2个String参数")
        void nowChatWithReport_shouldHave2Params() throws Exception {
            var method = MaxApp.class.getDeclaredMethod(
                    "nowChatWithReport", String.class, String.class);
            assertEquals(2, method.getParameterCount());
        }

        @Test
        @DisplayName("MaxApp - 应标注@Component注解")
        void maxApp_shouldHaveComponentAnnotation() {
            var annotation = MaxApp.class.getAnnotation(
                    org.springframework.stereotype.Component.class);
            assertNotNull(annotation, "MaxApp应标注@Component");
        }
    }
}