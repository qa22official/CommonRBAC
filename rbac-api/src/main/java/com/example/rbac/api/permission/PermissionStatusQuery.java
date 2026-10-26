package com.example.rbac.api.permission;

/**
 * Reports the health of the permission snapshot used by a resolution.
 * {@code degraded} is true when the answer may be based on stale data under the
 * fail-open policy (see 02 §11.3); callers expose it to clients so they can
 * apply their own fallback policy.
 */
public interface PermissionStatusQuery {

    /**
     * True when the answer may be based on stale data under fail-open.
     */
    boolean degraded();

    /**
     * True once a snapshot has been loaded successfully at least once.
     */
    boolean hasData();

    /**
     * False when the current policy requires rejecting the request: no data has
     * ever been loaded, or {@code stale-policy=fail-closed} and the snapshot is
     * stale.
     */
    boolean available();
}
