package com.example.rbac.api.permission;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of all permission facts. Permission codes for disabled
 * permissions are absent from {@code rolePermissions}; disabled roles remain in
 * {@code roleMeta} with {@code enabled = false}.
 */
public record PermissionSnapshot(
        Map<Long, Set<String>> rolePermissions,
        Map<Long, Set<Long>> userRoles,
        Map<Long, RoleMeta> roleMeta,
        Map<String, Long> permissionIds,
        long version,
        Instant loadedAt,
        Instant expiresAt,
        SnapshotState state) {

    public PermissionSnapshot {
        rolePermissions = immutableSets(rolePermissions);
        userRoles = immutableSets(userRoles);
        roleMeta = Map.copyOf(roleMeta);
        permissionIds = Map.copyOf(permissionIds);
        Objects.requireNonNull(loadedAt, "loadedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(state, "state");
    }

    public static PermissionSnapshot empty() {
        return new PermissionSnapshot(
                Map.<Long, Set<String>>of(),
                Map.<Long, Set<Long>>of(),
                Map.<Long, RoleMeta>of(),
                Map.<String, Long>of(),
                0L, Instant.EPOCH, Instant.EPOCH, SnapshotState.FAILED);
    }

    public boolean isStale(Instant now) {
        return state != SnapshotState.VALID || !expiresAt.isAfter(now);
    }

    public enum SnapshotState {
        VALID,
        STALE,
        RELOADING,
        FAILED
    }

    public record RoleMeta(boolean enabled, long inheritRoleId) {
    }

    private static <K, V> Map<K, Set<V>> immutableSets(Map<K, Set<V>> source) {
        Objects.requireNonNull(source, "source");
        var copy = new HashMap<K, Set<V>>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }
}
