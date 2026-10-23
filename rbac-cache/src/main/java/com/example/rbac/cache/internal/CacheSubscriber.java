package com.example.rbac.cache.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Pub/Sub 订阅端。收到 PERM_CHANGED:roleId / TOKEN_REVOKED:userId / TOKEN_REVOKED_ALL
 * 后只标记失效（invalidate），不立即重载。
 * Redis 不可用时 gracefully 跳过。
 */
@Component
public class CacheSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CacheSubscriber.class);
    private final PermissionCache cache;
    private final ObjectProvider<RedisConnectionFactory> factoryProvider;

    public CacheSubscriber(PermissionCache cache, ObjectProvider<RedisConnectionFactory> factoryProvider) {
        this.cache = cache;
        this.factoryProvider = factoryProvider;
    }

    @PostConstruct
    public void start() {
        RedisConnectionFactory factory = factoryProvider.getIfAvailable();
        if (factory == null) {
            log.info("rbac cache subscriber not started (redis not configured)");
            return;
        }
        try {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            container.addMessageListener(new MessageListenerAdapter(new Object() {
                @SuppressWarnings("unused") public void handleMessage(String payload) { onMessage(payload); }
            }), new ChannelTopic(PubMessages.CHANNEL));
            container.start();
            log.info("rbac cache subscriber started");
        } catch (Exception e) {
            log.warn("rbac cache subscriber not started (redis down?): {}", e.getMessage());
        }
    }

    private void onMessage(String payload) {
        if (payload == null || payload.isEmpty()) return;
        try {
            if (payload.equals(PubMessages.TOKEN_REVOKED_ALL)) {
                cache.markAllStale();
                return;
            }
            int colon = payload.indexOf(':');
            String type = colon < 0 ? payload : payload.substring(0, colon);
            long id = colon < 0 ? 0 : Long.parseLong(payload.substring(colon + 1));
            switch (type) {
                case PubMessages.PERM_CHANGED -> { cache.markRoleCodeStale(id); cache.markUserRolesStale(id); }
                case PubMessages.TOKEN_REVOKED -> cache.markIdentityStale(id);
                default -> log.debug("unknown cache pub message: {}", payload);
            }
        } catch (Exception e) {
            log.warn("rbac cache pub handle fail payload={}", payload, e);
        }
    }
}
