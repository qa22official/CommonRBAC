package com.example.rbac.cache.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis Pub/Sub implementation. Publish failures are counted and swallowed so a
 * Redis outage never rolls back a committed RBAC change.
 */
public class RedisCacheBus implements CacheBus {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheBus.class);
    private static final String VERSION_KEY = "rbac:cache:perm-version";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final String channel;
    private final Counter publishFailures;
    private final AtomicLong localFallback = new AtomicLong();

    public RedisCacheBus(StringRedisTemplate redis, String channel, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.channel = channel;
        this.publishFailures = Counter.builder("rbac.cache.broadcast.failures")
                .description("Redis invalidation publish failures")
                .register(meterRegistry);
    }

    @Override
    public long nextPermissionVersion() {
        try {
            Long value = redis.opsForValue().increment(VERSION_KEY);
            return value == null ? localFallback.incrementAndGet() : value;
        } catch (RuntimeException ex) {
            publishFailures.increment();
            log.warn("Redis unavailable while incrementing snapshot version: {}", ex.getMessage());
            return localFallback.incrementAndGet();
        }
    }

    @Override
    public void publishPermissionChanged(long version) {
        send(new CacheMessage(CacheMessage.PERM_CHANGED, version, null));
    }

    @Override
    public void publishTokenRevoked(long userId, long tokenVersion) {
        send(new CacheMessage(CacheMessage.TOKEN_REVOKED, tokenVersion, userId));
    }

    private void send(CacheMessage message) {
        try {
            redis.convertAndSend(channel, MAPPER.writeValueAsString(message));
        } catch (Exception ex) {
            publishFailures.increment();
            log.warn("Redis invalidation publish failed (soft TTL will converge): {}", ex.getMessage());
        }
    }
}
