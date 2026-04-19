package com.max.ai_agent.exception; // 注意包名要和你的项目匹配

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class  GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理所有不可知的 Exception 异常
     * 当 Controller 层抛出 Exception 时，会被这个方法捕获
     */
    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception e) {
        // 1. 打印异常日志，方便后端排查问题
        log.error("系统发生未知异常: ", e);

        // 2. 封装返回给前端的统一 JSON 格式数据
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "服务器内部错误，请稍后再试");
        // 注意：生产环境中，尽量不要把 e.getMessage() 直接返回给前端，以免暴露系统底层细节
        // result.put("errorDetail", e.getMessage());

        return result;
    }

    /**
     * 处理特定的异常，例如空指针异常 NullPointerException
     * Spring 会优先匹配最具体的异常类型
     */
    @ExceptionHandler(NullPointerException.class)
    public Map<String, Object> handleNullPointerException(NullPointerException e) {
        log.error("发生空指针异常: ", e);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "数据为空，请检查传递参数");

        return result;
    }

    // 你可以在这里继续添加自定义异常的拦截
    // @ExceptionHandler(CustomException.class)
    // public Map<String, Object> handleCustomException(CustomException e) { ... }
}