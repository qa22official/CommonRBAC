package com.example.rbac.api.support;

/** 记录型统一响应。成功用 code=0。 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null);
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(0, "ok", data, traceId);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    public static <T> ApiResponse<T> error(int code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }

    /** 语义别名，与 error 等价。 */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return error(code, message);
    }

    public static <T> ApiResponse<T> fail(int code, String message, String traceId) {
        return error(code, message, traceId);
    }
}
