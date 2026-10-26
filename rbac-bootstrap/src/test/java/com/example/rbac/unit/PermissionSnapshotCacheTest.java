package com.example.rbac.unit;

import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.api.permission.PermissionSnapshot;
import com.example.rbac.api.permission.PermissionSnapshotSource;
import com.example.rbac.cache.config.CacheProperties;
import com.example.rbac.cache.internal.PermissionSnapshotCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSnapshotCacheTest {

    private PermissionSnapshotCache cache(PermissionSnapshotSource source, CacheProperties properties) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        return new PermissionSnapshotCache(source, properties,
                beanFactory.getBeanProvider(AuditRecorder.class), new SimpleMeterRegistry());
    }

    private CacheProperties properties(String stalePolicy) {
        CacheProperties properties = new CacheProperties();
        properties.setSoftTtlSeconds(30);
        properties.setStalePolicy(stalePolicy);
        return properties;
    }

    private PermissionSnapshot snapshot(Map<Long, Set<Long>> userRoles,
                                        Map<Long, Set<String>> rolePermissions,
                                        Map<Long, PermissionSnapshot.RoleMeta> roleMeta) {
        Instant now = Instant.now();
        return new PermissionSnapshot(rolePermissions, userRoles, roleMeta, Map.of(),
                1, now, now.plusSeconds(30), PermissionSnapshot.SnapshotState.VALID);
    }

    @Test
    void userWithoutRolesHasNoPermissions() {
        PermissionSnapshotCache cache = cache(() -> snapshot(Map.of(), Map.of(), Map.of()), properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(cache.resolve(42L)).isEmpty();
    }

    @Test
    void singleAndMultipleRolesAreUnited() {
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(10L, 11L));
        Map<Long, Set<String>> rolePerms = Map.of(
                10L, Set.of("a:b:read"),
                11L, Set.of("a:b:write"));
        Map<Long, PermissionSnapshot.RoleMeta> meta = Map.of(
                10L, new PermissionSnapshot.RoleMeta(true, 0L),
                11L, new PermissionSnapshot.RoleMeta(true, 0L));
        PermissionSnapshotCache cache = cache(() -> snapshot(users, rolePerms, meta), properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(cache.resolve(1L)).containsExactlyInAnyOrder("a:b:read", "a:b:write");
    }

    @Test
    void disabledRoleIsFilteredOut() {
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(10L, 11L));
        Map<Long, Set<String>> rolePerms = Map.of(
                10L, Set.of("a:b:read"),
                11L, Set.of("a:b:write"));
        Map<Long, PermissionSnapshot.RoleMeta> meta = Map.of(
                10L, new PermissionSnapshot.RoleMeta(true, 0L),
                11L, new PermissionSnapshot.RoleMeta(false, 0L));
        PermissionSnapshotCache cache = cache(() -> snapshot(users, rolePerms, meta), properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(cache.resolve(1L)).containsExactly("a:b:read");
    }

    @Test
    void inheritanceCollectsTheChain() {
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(3L));
        Map<Long, Set<String>> rolePerms = Map.of(
                1L, Set.of("base:read"),
                2L, Set.of("mid:read"),
                3L, Set.of("top:read"));
        Map<Long, PermissionSnapshot.RoleMeta> meta = Map.of(
                1L, new PermissionSnapshot.RoleMeta(true, 0L),
                2L, new PermissionSnapshot.RoleMeta(true, 1L),
                3L, new PermissionSnapshot.RoleMeta(true, 2L));
        PermissionSnapshotCache cache = cache(() -> snapshot(users, rolePerms, meta), properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(cache.resolve(1L)).containsExactlyInAnyOrder("base:read", "mid:read", "top:read");
    }

    @Test
    void disabledRoleStopsTheUpstreamChain() {
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(3L));
        Map<Long, Set<String>> rolePerms = Map.of(
                1L, Set.of("base:read"),
                2L, Set.of("mid:read"),
                3L, Set.of("top:read"));
        Map<Long, PermissionSnapshot.RoleMeta> meta = Map.of(
                1L, new PermissionSnapshot.RoleMeta(true, 0L),
                2L, new PermissionSnapshot.RoleMeta(false, 1L),
                3L, new PermissionSnapshot.RoleMeta(true, 2L));
        PermissionSnapshotCache cache = cache(() -> snapshot(users, rolePerms, meta), properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(cache.resolve(1L)).containsExactly("top:read");
    }

    @Test
    void cycleFallsBackToDirectRoleOnly() {
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(2L));
        Map<Long, Set<String>> rolePerms = Map.of(
                2L, Set.of("two:read"),
                3L, Set.of("three:read"));
        Map<Long, PermissionSnapshot.RoleMeta> meta = Map.of(
                2L, new PermissionSnapshot.RoleMeta(true, 3L),
                3L, new PermissionSnapshot.RoleMeta(true, 2L));
        PermissionSnapshotCache cache = cache(() -> snapshot(users, rolePerms, meta), properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(cache.resolve(1L)).containsExactly("two:read");
    }

    @Test
    void fiveEdgesAreAllowedAndSixthIsSkipped() {
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(7L));
        Map<Long, Set<String>> rolePerms = new HashMap<>();
        Map<Long, PermissionSnapshot.RoleMeta> meta = new HashMap<>();
        for (long id = 1; id <= 7; id++) {
            rolePerms.put(id, Set.of("perm:" + id));
            meta.put(id, new PermissionSnapshot.RoleMeta(true, id == 1 ? 0L : id - 1));
        }
        PermissionSnapshotCache cache = cache(() -> snapshot(users, rolePerms, meta), properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(cache.resolve(1L))
                .containsExactlyInAnyOrder("perm:7", "perm:6", "perm:5", "perm:4", "perm:3", "perm:2")
                .doesNotContain("perm:1");
    }

    @Test
    void sixDirectRolesDoNotTriggerDepthGuard() {
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(1L, 2L, 3L, 4L, 5L, 6L));
        Map<Long, Set<String>> rolePerms = new HashMap<>();
        Map<Long, PermissionSnapshot.RoleMeta> meta = new HashMap<>();
        for (long id = 1; id <= 6; id++) {
            rolePerms.put(id, Set.of("perm:" + id));
            meta.put(id, new PermissionSnapshot.RoleMeta(true, 0L));
        }
        PermissionSnapshotCache cache = cache(() -> snapshot(users, rolePerms, meta), properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(cache.resolve(1L)).hasSize(6);
    }

    @Test
    void staleMarkTriggersSingleReload() throws InterruptedException {
        AtomicInteger loads = new AtomicInteger();
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(10L));
        Map<Long, Set<String>> rolePerms = Map.of(10L, Set.of("a:b:read"));
        Map<Long, PermissionSnapshot.RoleMeta> meta = Map.of(10L, new PermissionSnapshot.RoleMeta(true, 0L));
        PermissionSnapshotCache cache = cache(() -> {
            loads.incrementAndGet();
            return snapshot(users, rolePerms, meta);
        }, properties("fail-open"));
        cache.afterPropertiesSet();
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.resolve(1L)).containsExactly("a:b:read");
        cache.markStale();
        assertThat(cache.resolve(1L)).containsExactly("a:b:read");
        awaitLoads(loads, 2);
    }

    private void awaitLoads(AtomicInteger loads, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (loads.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(loads.get()).isEqualTo(expected);
    }

    @Test
    void reloadFailureKeepsOldDataUnderFailOpen() {
        AtomicBoolean fail = new AtomicBoolean(false);
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(10L));
        Map<Long, Set<String>> rolePerms = Map.of(10L, Set.of("a:b:read"));
        Map<Long, PermissionSnapshot.RoleMeta> meta = Map.of(10L, new PermissionSnapshot.RoleMeta(true, 0L));
        PermissionSnapshotCache cache = cache(() -> {
            if (fail.get()) {
                throw new IllegalStateException("database down");
            }
            return snapshot(users, rolePerms, meta);
        }, properties("fail-open"));
        cache.afterPropertiesSet();
        fail.set(true);
        cache.markStale();
        assertThat(cache.resolve(1L)).containsExactly("a:b:read");
        assertThat(cache.degraded()).isTrue();
        assertThat(cache.available()).isTrue();
        assertThat(cache.hasData()).isTrue();
    }

    @Test
    void reloadFailureRejectsUnderFailClosed() {
        AtomicBoolean fail = new AtomicBoolean(false);
        Map<Long, Set<Long>> users = Map.of(1L, Set.of(10L));
        Map<Long, Set<String>> rolePerms = Map.of(10L, Set.of("a:b:read"));
        Map<Long, PermissionSnapshot.RoleMeta> meta = Map.of(10L, new PermissionSnapshot.RoleMeta(true, 0L));
        PermissionSnapshotCache cache = cache(() -> {
            if (fail.get()) {
                throw new IllegalStateException("database down");
            }
            return snapshot(users, rolePerms, meta);
        }, properties("fail-closed"));
        cache.afterPropertiesSet();
        fail.set(true);
        cache.markStale();
        assertThat(cache.resolve(1L)).containsExactly("a:b:read");
        assertThat(cache.degraded()).isTrue();
        assertThat(cache.available()).isFalse();
    }
}
