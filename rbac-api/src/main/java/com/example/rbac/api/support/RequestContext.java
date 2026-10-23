package com.example.rbac.api.support;

import com.example.rbac.api.identity.CurrentUser;

/**
 * 请求上下文：每个请求的 CurrentUser 与 traceId 必须在过滤器早期写入，
 * 后续鉴权、审计、限流统一从这里读取。
 * 设计上：ThreadLocal + InheritableThreadLocal 的选择由实现方决定（鉴权链路无异步，ThreadLocal 即可）。
 */
public final class RequestContext {

    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    public static CurrentUser getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void setCurrentUser(CurrentUser user) {
        CURRENT_USER.set(user);
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static void clear() {
        CURRENT_USER.remove();
        TRACE_ID.remove();
    }
}
