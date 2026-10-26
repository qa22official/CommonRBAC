package com.example.rbac.cache.internal;

/**
 * Best-effort cross-instance notification. Failures never fail the business
 * transaction; the 30-second soft TTL is the fallback (01 FR-A9).
 */
public interface CacheBus {

    long nextPermissionVersion();

    void publishPermissionChanged(long version);

    void publishTokenRevoked(long userId, long tokenVersion);
}
