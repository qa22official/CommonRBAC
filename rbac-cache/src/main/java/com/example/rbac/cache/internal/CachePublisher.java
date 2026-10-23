package com.example.rbac.cache.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Pub/Sub 发布端。事务提交成功后调用 publish()，由调用方决定 payload。
 * 每次发布先本地失效（单实例即时生效，不依赖 Redis），再广播跨实例。
 * Redis 不可用时 gracefully 跳过（仅打 debug）。
 */
@Component
public class CachePublisher {

    private static final Logger log = LoggerFactory.getLogger(CachePublisher.class);
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final PermissionCache cache;

    public CachePublisher(ObjectProvider<StringRedisTemplate> redisProvider, PermissionCache cache) {
        this.redisProvider = redisProvider;
        this.cache = cache;
    }

    /** 发布 PERM_CHANGED —— 某角色的权限集合变了。 */
    public void publishPermChanged(long roleId) {
        cache.markRoleCodeStale(roleId);
        cache.markUserRolesStale(roleId);
        publish(PubMessages.PERM_CHANGED + ":" + roleId);
    }

    /** 发布 TOKEN_REVOKED —— 某用户令牌作废。 */
    public void publishTokenRevoked(long userId) {
        cache.markIdentityStale(userId);
        publish(PubMessages.TOKEN_REVOKED + ":" + userId);
    }

    /** 发布 TOKEN_REVOKED_ALL —— 全量令牌作废。 */
    public void publishAllRevoked() {
        cache.markAllStale();
        publish(PubMessages.TOKEN_REVOKED_ALL);
    }

    private void publish(String message) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            log.debug("rbac cache pub skipped (no redis): {}", message);
            return;
        }
        try {
            redis.convertAndSend(PubMessages.CHANNEL, message);
        } catch (Exception e) {
            log.warn("rbac cache pub failed (redis down?): {}", e.getMessage());
        }
    }
}
