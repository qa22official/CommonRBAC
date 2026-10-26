package com.example.rbac.api.support;

/**
 * Per-request facts shared by the guard, authentication, audit and admin
 * layers. Thread-local because the application uses synchronous servlet
 * handling only (see ADR-07); the filter clears it in a finally block.
 */
public final class RequestContext {

    public static final String CURRENT_USER_ATTRIBUTE = "rbac.currentUser";
    public static final String MUST_CHANGE_ATTRIBUTE = "rbac.mustChangePassword";
    public static final String AUTH_ERROR_ATTRIBUTE = "rbac.authError";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private RequestContext() {
    }

    public record Snapshot(Long userId, String username, String clientIp, String userAgent, String traceId) {
    }

    public static void set(Snapshot snapshot) {
        HOLDER.set(snapshot);
    }

    public static Snapshot get() {
        return HOLDER.get();
    }

    public static void setUser(long userId, String username) {
        Snapshot current = HOLDER.get();
        if (current == null) {
            HOLDER.set(new Snapshot(userId, username, null, null, null));
        } else {
            HOLDER.set(new Snapshot(userId, username, current.clientIp(), current.userAgent(), current.traceId()));
        }
    }

    public static Long userId() {
        Snapshot current = HOLDER.get();
        return current == null ? null : current.userId();
    }

    public static String username() {
        Snapshot current = HOLDER.get();
        return current == null ? null : current.username();
    }

    public static String clientIp() {
        Snapshot current = HOLDER.get();
        return current == null ? null : current.clientIp();
    }

    public static String userAgent() {
        Snapshot current = HOLDER.get();
        return current == null ? null : current.userAgent();
    }

    public static String traceId() {
        Snapshot current = HOLDER.get();
        return current == null ? null : current.traceId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
