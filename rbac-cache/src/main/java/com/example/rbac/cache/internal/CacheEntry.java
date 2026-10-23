package com.example.rbac.cache.internal;

import java.time.Instant;

/** 缓存条目：一份事实。携带加载时间、过期时间、状态。 */
public record CacheEntry<V>(
        V value,
        long cacheVersion,
        Instant loadedAt,
        Instant expiresAt,
        CacheState state) {

    public boolean expired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
