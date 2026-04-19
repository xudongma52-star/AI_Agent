package com.max.ai_agent.utils;

/**
 * 统一后端返回结果类
 * 使用泛型 <T> 是为了让 data 可以接收任何类型的数据（如 String, List, User 等）
 */
public class Result<T> {

    private Integer code;     // 状态码 (例如：200表示成功，500表示失败)
    private String message;   // 提示信息 (例如："操作成功", "密码错误")
    private T data;           // 实际返回的数据

    // 构造函数私有化，强制大家使用下面的静态方法（ok 和 error）
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ================== 成功的方法 ==================

    /**
     * 成功：不需要返回数据
     */
    public static <T> Result<T> ok() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 成功：需要返回数据
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功：自定义提示信息和数据
     */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }


    // ================== 失败的方法 ==================

    /**
     * 失败：只返回错误信息（默认500）
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 失败：自定义错误码和错误信息
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }


    // ================== Getter 和 Setter ==================
    // 如果你的项目里装了 Lombok 插件，删掉下面的 Getter/Setter，在类头上加个 @Data 注解即可
    
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}