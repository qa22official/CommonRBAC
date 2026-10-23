package com.example.rbac.cache.internal;

/** Redis Pub/Sub 消息类型。 */
public final class PubMessages {

    /** 某角色的权限集合变了 → 角色缓存 + 角色→权限缓存 → 依赖它的 userId→roles 也需要清 */
    public static final String PERM_CHANGED       = "PERM_CHANGED";
    /** 某用户的令牌被作废（登出/改密/停用/reset） */
    public static final String TOKEN_REVOKED      = "TOKEN_REVOKED";
    /** 全量令牌作废（密钥泄露应急） */
    public static final String TOKEN_REVOKED_ALL  = "TOKEN_REVOKED_ALL";

    /** RBAC 缓存 Pub/Sub 的 Redis channel。 */
    public static final String CHANNEL = "rbac.cache.invalidation";

    private PubMessages() {
    }
}
