package com.example.ai_chat.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code; // 状态码，200表示成功，其他表示失败
    private String message; // 提示信息
    private T data; // 返回的数据

    // 成功时的静态方法
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "操作成功";
        result.data = data;
        return result;
    }

    // 失败时的静态方法
    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.code = 500; // 默认错误码
        result.message = message;
        result.data = null;
        return result;
    }
}
