package com.example.rbac.cache.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks every snapshot stale when Redis becomes reachable again: broadcasts sent
 * while the connection was down are lost, and the soft TTL alone would take up
 * to 30 seconds to converge.
 */
@Component
@ConditionalOnProperty(name = "rbac.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConnectivityMonitor {

    private static final Logger log = LoggerFactory.getLogger(RedisConnectivityMonitor.class);

    private final RedisConnectionFactory connectionFactory;
    private final PermissionSnapshotCache permissionCache;
    private final IdentityCache identityCache;
    private volatile boolean reachable = true;

    public RedisConnectivityMonitor(RedisConnectionFactory connectionFactory,
                                    PermissionSnapshotCache permissionCache,
                                    IdentityCache identityCache) {
        this.connectionFactory = connectionFactory;
        this.permissionCache = permissionCache;
        this.identityCache = identityCache;
    }

    @Scheduled(fixedDelay = 10_000L)
    public void check() {
        boolean nowReachable = ping();
        if (!nowReachable && reachable) {
            log.warn("Redis is unreachable; invalidation falls back to the 30s soft TTL");
        }
        if (nowReachable && !reachable) {
            log.info("Redis reconnected; marking all permission and identity snapshots stale");
            permissionCache.markStale();
            identityCache.markAllStale();
        }
        reachable = nowReachable;
    }

    private boolean ping() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
