package com.example.rbac.api.support;

/**
 * HTTP 错误码。与 docs/00-编码代理工作基线 §5 口径一致。
 */
public final class ErrorCodes {

    public static final int OK = 0;

    /** 未认证（缺少合法 JWT、验签失败、未登录）。 */
    public static final int UNAUTHENTICATED = 401;

    /** 令牌已作废（token_version 不匹配或账号停用）。 */
    public static final int TOKEN_REVOKED = 40101;

    /** 无权限（身份已认证但缺少所需权限码）。 */
    public static final int FORBIDDEN = 403;

    /** 并发覆盖式授权版本不匹配。 */
    public static final int VERSION_CONFLICT = 40950;

    /** 降级：缓存重载失败且策略为 fail-closed，或数据库不可用且从未成功加载缓存。 */
    public static final int SERVICE_DEGRADED = 503;

    private ErrorCodes() {
    }
}
