package com.example.rbac.cache.config;

import com.example.rbac.cache.internal.CacheBus;
import com.example.rbac.cache.internal.CacheMessage;
import com.example.rbac.cache.internal.IdentityCache;
import com.example.rbac.cache.internal.NoopCacheBus;
import com.example.rbac.cache.internal.PermissionSnapshotCache;
import com.example.rbac.cache.internal.RedisCacheBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Wires Redis Pub/Sub when enabled and a local-only bus otherwise. The local
 * profile deliberately runs without Redis so the app is usable out of the box.
 */
@Configuration
public class RedisCacheConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfiguration.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    @ConditionalOnProperty(name = "rbac.redis.enabled", havingValue = "true", matchIfMissing = true)
    public CacheBus redisCacheBus(StringRedisTemplate redis,
                                  MeterRegistry meterRegistry,
                                  @Value("${rbac.cache.channel:rbac:cache:invalidate}") String channel) {
        return new RedisCacheBus(redis, channel, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(name = "rbac.redis.enabled", havingValue = "false")
    public CacheBus noopCacheBus() {
        return new NoopCacheBus();
    }

    @Bean
    @ConditionalOnProperty(name = "rbac.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            PermissionSnapshotCache permissionSnapshotCache,
            IdentityCache identityCache,
            @Value("${rbac.cache.channel:rbac:cache:invalidate}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                CacheMessage event = MAPPER.readValue(body, CacheMessage.class);
                if (CacheMessage.PERM_CHANGED.equals(event.type())) {
                    permissionSnapshotCache.onRemotePermissionChanged(event.version());
                } else if (CacheMessage.TOKEN_REVOKED.equals(event.type()) && event.userId() != null) {
                    identityCache.invalidate(event.userId());
                }
            } catch (Exception ex) {
                log.warn("Ignoring malformed cache invalidation message: {}", ex.getMessage());
            }
        }, new ChannelTopic(channel));
        return container;
    }
}
