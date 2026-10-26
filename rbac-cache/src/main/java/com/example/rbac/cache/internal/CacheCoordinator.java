package com.example.rbac.cache.internal;

import com.example.rbac.api.event.PermissionChangedEvent;
import com.example.rbac.api.event.TokenRevokedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives committed domain events and republishes an invalidation notice. The
 * local snapshot is marked stale immediately; Redis only accelerates the other
 * instances (02 §11.1).
 *
 * <p>Events inside a one-second window are coalesced, but the latest event is
 * always sent as a trailing notification. Publishing only the first event of a
 * burst would let a second permission change miss every remote instance until
 * its 30-second soft TTL expires, violating the one-second convergence
 * commitment in 01 FR-A9 / AC-3.</p>
 */
@Component
public class CacheCoordinator {

    private static final long DEBOUNCE_MILLIS = 1_000L;
    private static final String PERMISSION_KEY = "PERM";

    private final PermissionSnapshotCache permissionCache;
    private final IdentityCache identityCache;
    private final CacheBus bus;
    private final Map<String, Long> lastPublished = new ConcurrentHashMap<>();
    private final Map<String, PendingPermission> pendingPermissions = new ConcurrentHashMap<>();
    private final Map<Long, PendingToken> pendingTokens = new ConcurrentHashMap<>();

    public CacheCoordinator(PermissionSnapshotCache permissionCache,
                            IdentityCache identityCache,
                            CacheBus bus) {
        this.permissionCache = permissionCache;
        this.identityCache = identityCache;
        this.bus = bus;
    }

    @EventListener
    public void onPermissionChanged(PermissionChangedEvent event) {
        long version = bus.nextPermissionVersion();
        permissionCache.bumpVersion(version);
        permissionCache.markStale();

        long now = System.currentTimeMillis();
        if (shouldPublishNow(PERMISSION_KEY, now)) {
            bus.publishPermissionChanged(version);
            lastPublished.put(PERMISSION_KEY, now);
            pendingPermissions.remove(PERMISSION_KEY);
            return;
        }
        long dueAt = lastPublished.getOrDefault(PERMISSION_KEY, now) + DEBOUNCE_MILLIS;
        pendingPermissions.put(PERMISSION_KEY, new PendingPermission(version, dueAt));
    }

    @EventListener
    public void onTokenRevoked(TokenRevokedEvent event) {
        identityCache.invalidate(event.userId());

        String key = "TOKEN:" + event.userId();
        long now = System.currentTimeMillis();
        if (shouldPublishNow(key, now)) {
            bus.publishTokenRevoked(event.userId(), event.tokenVersion());
            lastPublished.put(key, now);
            pendingTokens.remove(event.userId());
            return;
        }
        long dueAt = lastPublished.getOrDefault(key, now) + DEBOUNCE_MILLIS;
        pendingTokens.put(event.userId(), new PendingToken(event.tokenVersion(), dueAt));
    }

    /**
     * Flushes the trailing notification for a coalesced burst. The fixed delay
     * is deliberately small relative to the one-second window so the added
     * latency stays within 01 FR-A9's normal-path commitment.
     */
    @Scheduled(fixedDelay = 200L)
    public void flushPending() {
        long now = System.currentTimeMillis();
        pendingPermissions.forEach((key, pending) -> {
            if (now >= pending.dueAt() && pendingPermissions.remove(key, pending)) {
                bus.publishPermissionChanged(pending.version());
                lastPublished.put(key, now);
            }
        });
        pendingTokens.forEach((userId, pending) -> {
            if (now >= pending.dueAt() && pendingTokens.remove(userId, pending)) {
                bus.publishTokenRevoked(userId, pending.tokenVersion());
                lastPublished.put("TOKEN:" + userId, now);
            }
        });
        if (lastPublished.size() > 10_000) {
            lastPublished.entrySet().removeIf(entry -> now - entry.getValue() > DEBOUNCE_MILLIS * 10);
        }
    }

    private boolean shouldPublishNow(String key, long now) {
        Long previous = lastPublished.get(key);
        return previous == null || now - previous >= DEBOUNCE_MILLIS;
    }

    private record PendingPermission(long version, long dueAt) {
    }

    private record PendingToken(long tokenVersion, long dueAt) {
    }
}
