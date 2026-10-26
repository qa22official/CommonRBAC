package com.example.rbac.cache.internal;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.api.permission.PermissionResolver;
import com.example.rbac.api.permission.PermissionSnapshot;
import com.example.rbac.api.permission.PermissionSnapshotSource;
import com.example.rbac.api.permission.PermissionStatusQuery;
import com.example.rbac.cache.config.CacheProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single immutable {@link PermissionSnapshot} with single-flight reload, a
 * 30-second soft TTL, and asynchronous refresh. Data is never discarded on
 * reload failure; {@code loadedAt} is only refreshed by a successful load
 * (02 §11.2 / §11.3).
 *
 * <p>STALE/expired snapshots are served immediately and refreshed by a
 * dedicated daemon thread. Blocking the request thread on a database reload
 * would turn a database outage into 30-second latency spikes and defeat the
 * fail-open posture required by 02 §11.2.</p>
 */
@Component
public class PermissionSnapshotCache implements PermissionResolver, PermissionStatusQuery, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PermissionSnapshotCache.class);
    private static final int MAX_DEPTH = 5;
    private static final long EXPLICIT_STALE_WAIT_MILLIS = 100L;
    private static final ExecutorService RELOAD_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rbac-permission-reload");
        thread.setDaemon(true);
        return thread;
    });

    private final PermissionSnapshotSource source;
    private final CacheProperties properties;
    private final ObjectProvider<AuditRecorder> auditRecorder;
    private final AtomicReference<PermissionSnapshot> current =
            new AtomicReference<>(PermissionSnapshot.empty());
    private final ReentrantLock reloadLock = new ReentrantLock();
    private final AtomicBoolean reloading = new AtomicBoolean(false);
    private final AtomicBoolean degradationReported = new AtomicBoolean(false);
    private final AtomicLong version = new AtomicLong();
    private final AtomicLong invalidationGeneration = new AtomicLong();
    private final AtomicLong reloadFailureCount = new AtomicLong();
    private final AtomicLong staleAllowedCount = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant nextRetryAt = Instant.EPOCH;

    private final Counter reloads;
    private final Counter reloadFailures;
    private final Counter staleAllowed;
    private final Timer reloadTimer;

    public PermissionSnapshotCache(PermissionSnapshotSource source,
                                   CacheProperties properties,
                                   ObjectProvider<AuditRecorder> auditRecorder,
                                   MeterRegistry meterRegistry) {
        this.source = source;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        this.reloads = Counter.builder("rbac.cache.permission.reloads")
                .description("Successful permission snapshot reloads")
                .register(meterRegistry);
        this.reloadTimer = Timer.builder("rbac.cache.permission.reload.duration")
                .description("Permission snapshot reload latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        Gauge.builder("rbac.cache.permission.age.seconds", this, PermissionSnapshotCache::ageSeconds)
                .description("Seconds since the last successful snapshot load")
                .register(meterRegistry);
        Gauge.builder("rbac.cache.permission.users", this, PermissionSnapshotCache::affectedUsers)
                .description("Users whose permission answer may be stale")
                .register(meterRegistry);
        Gauge.builder("rbac.cache.permission.version", this, PermissionSnapshotCache::currentVersion)
                .description("Last observed snapshot version")
                .register(meterRegistry);
        Gauge.builder("rbac.cache.permission.has_data", this, cache -> cache.hasData() ? 1 : 0)
                .description("Whether a snapshot has ever loaded successfully")
                .register(meterRegistry);
        Gauge.builder("rbac.cache.permission.degraded", this, cache -> cache.degraded() ? 1 : 0)
                .description("Whether permission answers currently use stale data")
                .register(meterRegistry);
        this.staleAllowed = Counter.builder("rbac.cache.permission.stale.allowed")
                .description("Requests answered with a stale permission snapshot under fail-open")
                .register(meterRegistry);
        this.reloadFailures = Counter.builder("rbac.cache.permission.reload.failures")
                .description("Failed permission snapshot reloads")
                .register(meterRegistry);
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isLoadOnStartup()) {
            return;
        }
        try {
            reloadBlocking();
        } catch (RuntimeException ex) {
            log.error("Initial permission snapshot load failed (type={}); requests will retry",
                    ex.getClass().getName());
        }
    }

    @Override
    public Set<String> resolve(long userId) {
        PermissionSnapshot snapshot = ensureFresh();
        if (!hasData(snapshot)) {
            return Set.of();
        }
        if (degraded(snapshot) && available(snapshot)) {
            staleAllowedCount.incrementAndGet();
            staleAllowed.increment();
        }
        return Set.copyOf(compute(snapshot, userId));
    }

    @Override
    public boolean degraded() {
        return degraded(current.get());
    }

    @Override
    public boolean hasData() {
        return hasData(current.get());
    }

    @Override
    public boolean available() {
        // Under fail-closed a stale snapshot is rejected immediately, but the
        // refresh is still triggered so recovery does not need another request.
        return available(ensureFresh());
    }

    public long currentVersion() {
        return version.get();
    }

    /**
     * Marks the snapshot stale without dropping the data. The next request
     * triggers the asynchronous single-flight reload (UC-4).
     */
    public void markStale() {
        invalidationGeneration.incrementAndGet();
        current.updateAndGet(snapshot -> {
            if (!hasData(snapshot)) {
                return snapshot;
            }
            return copy(snapshot, PermissionSnapshot.SnapshotState.STALE);
        });
    }

    public long bumpVersion(long eventVersion) {
        if (eventVersion > 0) {
            version.accumulateAndGet(eventVersion, Math::max);
        } else {
            version.incrementAndGet();
        }
        return version.get();
    }

    public void onRemotePermissionChanged(long remoteVersion) {
        if (remoteVersion > 0) {
            version.accumulateAndGet(remoteVersion, Math::max);
        }
        markStale();
    }

    private PermissionSnapshot ensureFresh() {
        PermissionSnapshot snapshot = current.get();
        Instant now = Instant.now();
        if (hasData(snapshot) && snapshot.state() == PermissionSnapshot.SnapshotState.VALID
                && snapshot.expiresAt().isAfter(now)) {
            return snapshot;
        }
        // Explicit invalidation (a committed permission change) is waited on
        // briefly so a normal local reload is visible to the very next request;
        // a slow/failing database only costs this bounded wait and then the old
        // snapshot is served (fail-open). Soft-TTL expiry is fully
        // asynchronous, so a long outage never turns into 30-second requests.
        boolean started = triggerAsyncReload();
        if (started && hasData(snapshot) && snapshot.state() == PermissionSnapshot.SnapshotState.STALE) {
            return awaitReload();
        }
        return snapshot;
    }

    private PermissionSnapshot awaitReload() {
        long deadline = System.nanoTime() + EXPLICIT_STALE_WAIT_MILLIS * 1_000_000L;
        while (reloading.get() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(2L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return current.get();
    }

    private boolean triggerAsyncReload() {
        if (Instant.now().isBefore(nextRetryAt)) {
            return false;
        }
        if (!reloading.compareAndSet(false, true)) {
            return false;
        }
        try {
            RELOAD_EXECUTOR.execute(this::reloadAsync);
            return true;
        } catch (RuntimeException ex) {
            reloading.set(false);
            log.warn("Could not schedule permission snapshot reload: {}", ex.getMessage());
            return false;
        }
    }

    private void reloadAsync() {
        reloadLock.lock();
        try {
            long generation = invalidationGeneration.get();
            long start = System.nanoTime();
            PermissionSnapshot loaded = source.load();
            Instant loadedAt = Instant.now();
            PermissionSnapshot.SnapshotState state = invalidationGeneration.get() == generation
                    ? PermissionSnapshot.SnapshotState.VALID
                    : PermissionSnapshot.SnapshotState.STALE;
            PermissionSnapshot fresh = new PermissionSnapshot(
                    loaded.rolePermissions(),
                    loaded.userRoles(),
                    loaded.roleMeta(),
                    loaded.permissionIds(),
                    version.get(),
                    loadedAt,
                    loadedAt.plusSeconds(properties.getSoftTtlSeconds()),
                    state);
            current.set(fresh);
            reloads.increment();
            reloadTimer.record(Duration.ofNanos(System.nanoTime() - start));
            consecutiveFailures.set(0);
            nextRetryAt = Instant.EPOCH;
            degradationReported.set(false);
            log.info("Permission snapshot reloaded: roles={}, users={}, permissions={}, state={}",
                    fresh.roleMeta().size(), fresh.userRoles().size(), fresh.permissionIds().size(), state);
        } catch (RuntimeException ex) {
            recordFailure(current.get(), ex);
        } finally {
            reloading.set(false);
            reloadLock.unlock();
        }
    }

    private PermissionSnapshot reloadBlocking() {
        if (!reloadLock.tryLock()) {
            return current.get();
        }
        try {
            reloading.set(true);
            long generation = invalidationGeneration.get();
            long start = System.nanoTime();
            PermissionSnapshot loaded = source.load();
            Instant loadedAt = Instant.now();
            PermissionSnapshot.SnapshotState state = invalidationGeneration.get() == generation
                    ? PermissionSnapshot.SnapshotState.VALID
                    : PermissionSnapshot.SnapshotState.STALE;
            PermissionSnapshot fresh = new PermissionSnapshot(
                    loaded.rolePermissions(),
                    loaded.userRoles(),
                    loaded.roleMeta(),
                    loaded.permissionIds(),
                    version.get(),
                    loadedAt,
                    loadedAt.plusSeconds(properties.getSoftTtlSeconds()),
                    state);
            current.set(fresh);
            reloads.increment();
            reloadTimer.record(Duration.ofNanos(System.nanoTime() - start));
            consecutiveFailures.set(0);
            nextRetryAt = Instant.EPOCH;
            degradationReported.set(false);
            log.info("Permission snapshot loaded at startup: roles={}, users={}, permissions={}",
                    fresh.roleMeta().size(), fresh.userRoles().size(), fresh.permissionIds().size());
            return fresh;
        } catch (RuntimeException ex) {
            recordFailure(current.get(), ex);
            return current.get();
        } finally {
            reloading.set(false);
            reloadLock.unlock();
        }
    }

    private void recordFailure(PermissionSnapshot previous, RuntimeException ex) {
        int failures = consecutiveFailures.incrementAndGet();
        long delaySeconds = Math.min(30L, 1L << Math.min(failures, 4));
        nextRetryAt = Instant.now().plusSeconds(delaySeconds);
        if (hasData(previous)) {
            current.set(copy(previous, PermissionSnapshot.SnapshotState.FAILED));
        } else {
            current.set(PermissionSnapshot.empty());
        }
        log.error("Permission snapshot reload failed (attempt {}), retry in {}s: {}",
                failures, delaySeconds, ex.getMessage());
        if (degradationReported.compareAndSet(false, true)) {
            recordDegradationAudit(ex);
        }
    }

    private void recordDegradationAudit(RuntimeException ex) {
        AuditRecorder recorder = auditRecorder.getIfAvailable();
        if (recorder == null) {
            return;
        }
        try {
            recorder.record(new AuditRecord(
                    AuditCategory.SECURITY,
                    null,
                    "system",
                    "CACHE_DEGRADED",
                    "CACHE",
                    null,
                    null,
                    "{\"policy\":\"" + properties.getStalePolicy() + "\"}",
                    false,
                    "权限快照重载失败",
                    "internal",
                    null,
                    null,
                    null,
                    Instant.now(),
                    null));
        } catch (RuntimeException auditFailure) {
            log.warn("Could not record cache degradation audit: {}", auditFailure.getMessage());
        }
    }

    private Set<String> compute(PermissionSnapshot snapshot, long userId) {
        Set<Long> directRoles = snapshot.userRoles().getOrDefault(userId, Set.of());
        Set<String> codes = new HashSet<>();
        for (Long roleId : directRoles) {
            PermissionSnapshot.RoleMeta meta = snapshot.roleMeta().get(roleId);
            if (meta == null || !meta.enabled()) {
                continue;
            }
            List<Long> path = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            long cursor = roleId;
            int edges = 0;
            boolean cycle = false;
            while (true) {
                if (!seen.add(cursor)) {
                    cycle = true;
                    break;
                }
                PermissionSnapshot.RoleMeta node = snapshot.roleMeta().get(cursor);
                if (node == null || !node.enabled()) {
                    break;
                }
                path.add(cursor);
                long next = node.inheritRoleId();
                if (next == 0L) {
                    break;
                }
                if (edges + 1 > MAX_DEPTH) {
                    log.warn("Role inheritance depth exceeded at role {}; branch skipped", roleId);
                    break;
                }
                edges++;
                cursor = next;
            }
            if (cycle) {
                log.warn("Role inheritance cycle detected at role {}; falling back to direct role", roleId);
                codes.addAll(snapshot.rolePermissions().getOrDefault(roleId, Set.of()));
                continue;
            }
            for (Long effective : path) {
                codes.addAll(snapshot.rolePermissions().getOrDefault(effective, Set.of()));
            }
        }
        return codes;
    }

    private boolean degraded(PermissionSnapshot snapshot) {
        if (!hasData(snapshot)) {
            return false;
        }
        return snapshot.state() != PermissionSnapshot.SnapshotState.VALID
                || !snapshot.expiresAt().isAfter(Instant.now());
    }

    private boolean available(PermissionSnapshot snapshot) {
        if (!hasData(snapshot)) {
            return false;
        }
        if (properties.failClosed() && degraded(snapshot)) {
            return false;
        }
        return true;
    }

    private boolean hasData(PermissionSnapshot snapshot) {
        return snapshot != null && !Instant.EPOCH.equals(snapshot.loadedAt());
    }

    private PermissionSnapshot copy(PermissionSnapshot snapshot, PermissionSnapshot.SnapshotState state) {
        return new PermissionSnapshot(
                snapshot.rolePermissions(),
                snapshot.userRoles(),
                snapshot.roleMeta(),
                snapshot.permissionIds(),
                snapshot.version(),
                snapshot.loadedAt(),
                snapshot.expiresAt(),
                state);
    }

    private double ageSeconds() {
        PermissionSnapshot snapshot = current.get();
        if (!hasData(snapshot)) {
            return -1;
        }
        return Duration.between(snapshot.loadedAt(), Instant.now()).toMillis() / 1000.0;
    }

    private double affectedUsers() {
        PermissionSnapshot snapshot = current.get();
        return degraded(snapshot) ? snapshot.userRoles().size() : 0;
    }

    long reloadFailureCount() {
        return reloadFailureCount.get();
    }

    long staleAllowedCount() {
        return staleAllowedCount.get();
    }
}
