package com.example.rbac.api.permission;

/**
 * Loads the complete permission snapshot in one call.
 * The cache layer performs single-flight replacement around this source.
 */
public interface PermissionSnapshotSource {

    PermissionSnapshot load();
}
