package com.example.rbac.cache.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Local-only bus used when Redis is disabled (local profile / tests). The soft
 * TTL still bounds staleness.
 */
public class NoopCacheBus implements CacheBus {

    private static final Logger log = LoggerFactory.getLogger(NoopCacheBus.class);
    private final AtomicLong version = new AtomicLong();

    @Override
    public long nextPermissionVersion() {
        return version.incrementAndGet();
    }

    @Override
    public void publishPermissionChanged(long version) {
        log.debug("Redis disabled; PERM_CHANGED version {} stays local", version);
    }

    @Override
    public void publishTokenRevoked(long userId, long tokenVersion) {
        log.debug("Redis disabled; TOKEN_REVOKED user {} stays local", userId);
    }
}
