package com.example.rbac.api.support;

public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(0, "ok", data, traceId);
    }
}
