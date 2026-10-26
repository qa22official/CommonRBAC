package com.example.rbac.cache.internal;

import com.example.rbac.api.identity.IdentityQuery;
import com.example.rbac.api.identity.IdentitySnapshot;
import com.example.rbac.api.identity.IdentitySnapshotSource;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.cache.config.CacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user identity snapshot with a 30-second soft TTL. On a reload failure an
 * existing entry is reused under fail-open; a user whose token was just revoked
 * is rejected instead of falling back to stale data (02 §11.3).
 *
 * <p>Loads and invalidations for the same user are serialized by a fixed stripe
 * lock. This closes the race where an in-flight load started before a
 * TOKEN_REVOKED could put the old tokenVersion back into the cache and remove
 * the revocation marker after the invalidation. The cache deliberately has no
 * hard time-based eviction: the 30-second TTL is a refresh hint, not an
 * eviction trigger (02 §11.2), and a successful load must remain usable during
 * a database outage.</p>
 */
@Component
public class IdentityCache implements IdentityQuery {

    private static final Logger log = LoggerFactory.getLogger(IdentityCache.class);
    private static final int LOCK_STRIPES = 256;

    private final IdentitySnapshotSource source;
    private final CacheProperties properties;
    private final Cache<Long, Entry> cache;
    private final ConcurrentHashMap<Long, Long> revokedAt = new ConcurrentHashMap<>();
    private final Object[] locks = new Object[LOCK_STRIPES];
    private final Counter hits;
    private final Counter misses;
    private final Counter failures;

    public IdentityCache(IdentitySnapshotSource source,
                         CacheProperties properties,
                         MeterRegistry meterRegistry) {
        this.source = source;
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(200_000)
                .build();
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        this.hits = Counter.builder("rbac.cache.identity.hits")
                .description("Identity snapshot cache hits")
                .register(meterRegistry);
        this.misses = Counter.builder("rbac.cache.identity.misses")
                .description("Identity snapshot cache loads")
                .register(meterRegistry);
        this.failures = Counter.builder("rbac.cache.identity.load.failures")
                .description("Identity snapshot load failures")
                .register(meterRegistry);
        Gauge.builder("rbac.cache.identity.size", cache, c -> c.estimatedSize())
                .description("Identity snapshot cache entries")
                .register(meterRegistry);
        Gauge.builder("rbac.cache.identity.revoked", revokedAt, map -> map.size())
                .description("Users with a recently revoked token")
                .register(meterRegistry);
    }

    @Override
    public Optional<IdentitySnapshot> findByUserId(long userId) {
        long now = System.currentTimeMillis();
        Entry entry = cache.getIfPresent(userId);
        if (entry != null && entry.expiresAtMillis() > now) {
            hits.increment();
            return materialize(entry);
        }
        synchronized (lockFor(userId)) {
            long lockedNow = System.currentTimeMillis();
            entry = cache.getIfPresent(userId);
            if (entry != null && entry.expiresAtMillis() > lockedNow) {
                hits.increment();
                return materialize(entry);
            }
            try {
                Optional<IdentitySnapshot> loaded = source.load(userId);
                misses.increment();
                Entry fresh = loaded
                        .map(snapshot -> new Entry(snapshot,
                                lockedNow + properties.getSoftTtlSeconds() * 1000L, false))
                        .orElseGet(() -> new Entry(null,
                                lockedNow + properties.getSoftTtlSeconds() * 1000L, true));
                cache.put(userId, fresh);
                revokedAt.remove(userId);
                return loaded;
            } catch (RuntimeException ex) {
                failures.increment();
                if (revokedAt.containsKey(userId)) {
                    log.warn("Identity reload failed for revoked user {}; rejecting", userId);
                    return Optional.of(revokedSnapshot(userId));
                }
                if (!properties.failClosed() && entry != null) {
                    log.warn("Identity reload failed for user {}; serving last known value: {}",
                            userId, ex.getMessage());
                    return materialize(entry);
                }
                log.warn("Identity reload failed for user {}; rejecting under {}: {}",
                        userId, properties.getStalePolicy(), ex.getMessage());
                throw new BusinessException(ErrorCodes.SERVICE_DEGRADED, "身份数据暂不可用");
            }
        }
    }

    public void invalidate(long userId) {
        synchronized (lockFor(userId)) {
            cache.invalidate(userId);
            revokedAt.put(userId, System.currentTimeMillis());
        }
    }

    /**
     * Marks every entry expired without discarding it. The next access reloads;
     * if the database is still unavailable, the last known value remains usable
     * under fail-open (02 §11.1 / §11.2). The revoked set is intentionally kept.
     */
    public void markAllStale() {
        cache.asMap().replaceAll((userId, entry) ->
                new Entry(entry.snapshot(), 0L, entry.negative()));
    }

    private Object lockFor(long userId) {
        return locks[(int) (userId & (LOCK_STRIPES - 1))];
    }

    private Optional<IdentitySnapshot> materialize(Entry entry) {
        return entry.negative() ? Optional.empty() : Optional.ofNullable(entry.snapshot());
    }

    private IdentitySnapshot revokedSnapshot(long userId) {
        Instant now = Instant.now();
        return new IdentitySnapshot(userId, Long.MAX_VALUE, false, false,
                now, now.plusSeconds(properties.getSoftTtlSeconds()));
    }

    private record Entry(IdentitySnapshot snapshot, long expiresAtMillis, boolean negative) {
    }
}
