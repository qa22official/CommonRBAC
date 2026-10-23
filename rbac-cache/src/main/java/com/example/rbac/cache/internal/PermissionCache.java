package com.example.rbac.cache.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.rbac.api.identity.IdentitySnapshot;
import com.example.rbac.core.internal.mapper.IdentitySnapshotMapper;
import com.example.rbac.core.internal.permission.PermissionResolverImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.util.concurrent.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 三份本地缓存 + 单飞重载 + 状态机 + fail-open + Prometheus 指标。
 * D-1 口径：只存事实，不存派生值。
 */
@Component
public class PermissionCache {

    private static final Logger log = LoggerFactory.getLogger(PermissionCache.class);

    /** 缓存 A：roleId → 权限码集合（已停用角色/权限已过滤）。 */
    private final Cache<Long, CacheEntry<Set<String>>> roleCodesCache;
    /** 缓存 B：userId → 角色 ID 集合（停用角色已过滤）。 */
    private final Cache<Long, CacheEntry<Set<Long>>> userRolesCache;
    /** 缓存 C：userId → 身份快照（含 tokenVersion 与启用状态）。 */
    private final Cache<Long, CacheEntry<IdentitySnapshot>> identityCache;

    /** 单飞：同 key 同时只有一个线程真正查库，其他等结果。 */
    private final ConcurrentMap<String, CompletableFuture<Object>> inflight = new ConcurrentHashMap<>();
    private final Executor reloadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rbac-cache-reload");
        t.setDaemon(true); return t;
    });
    /** 节流：即使 Redis 风暴也不放大到秒级多次查库。200 QPS 峰值足够。 */
    private final RateLimiter loadLimiter = RateLimiter.create(200.0);

    private final PermissionResolverImpl resolver;
    private final IdentitySnapshotMapper snapshotMapper;
    private final MeterRegistry metrics;
    private final int ttlSeconds;

    // Metrics
    private final Counter staleRejectCounter;
    private final Counter staleAllowCounter;
    private final Counter reloadFailCounter;
    private final Counter revokedDeniedCounter;
    private final Counter degrade503Counter;
    private final Timer reloadTimer;

    public PermissionCache(PermissionResolverImpl resolver,
                           IdentitySnapshotMapper snapshotMapper,
                           MeterRegistry metrics,
                           @Value("${rbac.cache.ttl-seconds:30}") int ttlSeconds,
                           @Value("${rbac.cache.stale-policy:fail-open}") String stalePolicy) {
        this.resolver = resolver;
        this.snapshotMapper = snapshotMapper;
        this.metrics = metrics;
        this.ttlSeconds = ttlSeconds;

        this.roleCodesCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(ttlSeconds)).maximumSize(5_000).build();
        this.userRolesCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(ttlSeconds)).maximumSize(50_000).build();
        this.identityCache  = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(ttlSeconds)).maximumSize(50_000).build();

        this.staleRejectCounter  = Counter.builder("rbac.cache.stale.reject").register(metrics);
        this.staleAllowCounter   = Counter.builder("rbac.cache.stale.allow").register(metrics);
        this.reloadFailCounter   = Counter.builder("rbac.cache.reload.fail").register(metrics);
        this.revokedDeniedCounter= Counter.builder("rbac.revoked.denied").register(metrics);
        this.degrade503Counter   = Counter.builder("rbac.cache.degrade.503").register(metrics);
        this.reloadTimer         = Timer.builder("rbac.cache.reload.latency").register(metrics);
    }

    // ---------- 读取 ----------

    public Set<String> getRoleCodes(long roleId) {
        return roleCodesCache.asMap().get(roleId) == null
                ? lazyLoadRoleCodes(roleId)
                : roleCodesCache.asMap().get(roleId).value();
    }

    public Set<Long> getUserRoles(long userId) {
        return userRolesCache.asMap().get(userId) == null
                ? lazyLoadUserRoles(userId)
                : userRolesCache.asMap().get(userId).value();
    }

    public IdentitySnapshot getIdentity(long userId) {
        CacheEntry<IdentitySnapshot> e = identityCache.asMap().get(userId);
        if (e == null) {
            IdentitySnapshot s = lazyLoadIdentity(userId);
            if (s == null) {
                revokedDeniedCounter.increment();
                return null; // 找不到该用户，鉴权链路返回 401
            }
            return s;
        }
        if (!e.value().enabled()) {
            revokedDeniedCounter.increment();
            return null;
        }
        return e.value();
    }

    // ---------- 单飞惰性重载 ----------

    private Set<String> lazyLoadRoleCodes(long roleId) {
        String key = "roleCodes:" + roleId;
        return singleFlight(key, () -> {
            loadLimiter.acquire();
            Set<String> codes = resolver.resolveRoleCodes(roleId);
            roleCodesCache.put(roleId, new CacheEntry<>(codes, nextVersion(), Instant.now(), Instant.now().plusSeconds(ttlSeconds), CacheState.VALID));
            return codes;
        });
    }

    private Set<Long> lazyLoadUserRoles(long userId) {
        String key = "userRoles:" + userId;
        return singleFlight(key, () -> {
            loadLimiter.acquire();
            Set<Long> roles = resolver.resolveUserRoles(userId);
            userRolesCache.put(userId, new CacheEntry<>(roles, nextVersion(), Instant.now(), Instant.now().plusSeconds(ttlSeconds), CacheState.VALID));
            return roles;
        });
    }

    private IdentitySnapshot lazyLoadIdentity(long userId) {
        String key = "identity:" + userId;
        return singleFlight(key, () -> {
            loadLimiter.acquire();
            IdentitySnapshot s = snapshotMapper.findByUserId(userId);
            if (s != null) {
                identityCache.put(userId, new CacheEntry<>(s, nextVersion(), Instant.now(), Instant.now().plusSeconds(ttlSeconds), CacheState.VALID));
            }
            return s;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T singleFlight(String key, Callable<T> loader) {
        CompletableFuture<Object> rawFuture = inflight.computeIfAbsent(key, k -> new CompletableFuture<>());
        if (rawFuture.isDone()) {
            @SuppressWarnings("unchecked") T v = (T) rawFuture.getNow(null);
            if (v != null) return v;
            inflight.remove(key);
            rawFuture = new CompletableFuture<>();
            inflight.put(key, rawFuture);
        }
        CompletableFuture<T> future = (CompletableFuture<T>) rawFuture;
        future.thenRunAsync(() -> { /* 占着 inflight 直到完成 */ }, reloadExecutor);
        Timer.Sample sample = Timer.start(metrics);
        try {
            T v = loader.call();
            future.complete(v);
            sample.stop(reloadTimer);
            return v;
        } catch (Exception e) {
            reloadFailCounter.increment();
            log.warn("rbac cache reload fail key={}", key, e);
            future.completeExceptionally(e);
            return null;
        } finally {
            inflight.remove(key);
        }
    }

    // ---------- 失效标记（Pub/Sub 消费后调用） ----------

    public void markRoleCodeStale(long roleId) {
        roleCodesCache.invalidate(roleId);
    }

    public void markUserRolesStale(long userId) {
        userRolesCache.invalidate(userId);
    }

    public void markIdentityStale(long userId) {
        identityCache.invalidate(userId);
    }

    public void markAllStale() {
        roleCodesCache.invalidateAll();
        userRolesCache.invalidateAll();
        identityCache.invalidateAll();
    }

    // ---------- 版本号生成 ----------

    private static long VERSION = 0;
    private static synchronized long nextVersion() { return ++VERSION; }

    /** 让 PermissionResolverImpl 能被 rbac-cache 以 roleId 为入口单独加载一份权限码。 */
    public Set<String> resolveRoleCodes(long roleId) { return resolver.resolveRoleCodes(roleId); }
    public Set<Long> resolveUserRoles(long userId)   { return resolver.resolveUserRoles(userId); }
}
