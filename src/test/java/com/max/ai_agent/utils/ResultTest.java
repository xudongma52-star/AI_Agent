package com.max.ai_agent.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Result 统一返回类 单元测试")
class ResultTest {

    @Nested
    @DisplayName("ok() 成功方法测试")
    class OkTests {

        @Test
        @DisplayName("ok() 无参 - 应返回200，data为null")
        void ok_noArgs_shouldReturn200WithNullData() {
            Result<Object> result = Result.ok();
            assertAll(
                () -> assertEquals(200, result.getCode()),
                () -> assertEquals("操作成功", result.getMessage()),
                () -> assertNull(result.getData())
            );
        }

        @Test
        @DisplayName("ok(data) 携带String - 应正确返回")
        void ok_withStringData_shouldReturnData() {
            Result<String> result = Result.ok("Hello AI");
            assertAll(
                () -> assertEquals(200, result.getCode()),
                () -> assertEquals("Hello AI", result.getData())
            );
        }

        @Test
        @DisplayName("ok(data) 携带List - 泛型应正常工作")
        void ok_withListData_shouldHandleGeneric() {
            List<String> data = List.of("msg1", "msg2", "msg3");
            Result<List<String>> result = Result.ok(data);
            assertAll(
                () -> assertNotNull(result.getData()),
                () -> assertEquals(3, result.getData().size()),
                () -> assertEquals("msg1", result.getData().get(0))
            );
        }

        @Test
        @DisplayName("ok(message, data) 自定义消息")
        void ok_withCustomMessage_shouldUseIt() {
            Result<Integer> result = Result.ok("查询成功", 42);
            assertAll(
                () -> assertEquals(200, result.getCode()),
                () -> assertEquals("查询成功", result.getMessage()),
                () -> assertEquals(42, result.getData())
            );
        }

        @Test
        @DisplayName("ok(data) 传null - 不应抛异常")
        void ok_withNullData_shouldNotThrow() {
            assertDoesNotThrow(() -> {
                Result<String> result = Result.ok((String) null);
                assertEquals(200, result.getCode());
                assertNull(result.getData());
            });
        }
    }

    @Nested
    @DisplayName("error() 失败方法测试")
    class ErrorTests {

        @Test
        @DisplayName("error(message) - 应返回500，data为null")
        void error_messageOnly_shouldReturn500() {
            Result<Object> result = Result.error("系统繁忙");
            assertAll(
                () -> assertEquals(500, result.getCode()),
                () -> assertEquals("系统繁忙", result.getMessage()),
                () -> assertNull(result.getData())
            );
        }

        @Test
        @DisplayName("error(code, message) - 应使用自定义code")
        void error_customCode_shouldUseIt() {
            Result<Object> result = Result.error(404, "资源不存在");
            assertAll(
                () -> assertEquals(404, result.getCode()),
                () -> assertEquals("资源不存在", result.getMessage())
            );
        }

        @Test
        @DisplayName("error(401) - 未授权场景")
        void error_401_shouldWork() {
            Result<Object> result = Result.error(401, "未登录");
            assertEquals(401, result.getCode());
        }
    }

    @Nested
    @DisplayName("Setter 方法测试")
    class SetterTests {

        @Test
        @DisplayName("setCode/setMessage/setData - 应能修改所有字段")
        void setters_shouldUpdateAllFields() {
            Result<String> result = Result.ok();
            result.setCode(201);
            result.setMessage("已创建");
            result.setData("新数据");
            assertAll(
                () -> assertEquals(201, result.getCode()),
                () -> assertEquals("已创建", result.getMessage()),
                () -> assertEquals("新数据", result.getData())
            );
        }
    }

    @Nested
    @DisplayName("边界场景测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("error 空字符串消息 - 不应抛异常")
        void error_emptyMessage_shouldNotThrow() {
            assertDoesNotThrow(() -> {
                Result<Object> result = Result.error("");
                assertEquals("", result.getMessage());
            });
        }

        @Test
        @DisplayName("ok null消息 - 不应抛异常")
        void ok_nullMessage_shouldNotThrow() {
            assertDoesNotThrow(() -> {
                Result<String> result = Result.ok(null, "data");
                assertNull(result.getMessage());
            });
        }
    }
}