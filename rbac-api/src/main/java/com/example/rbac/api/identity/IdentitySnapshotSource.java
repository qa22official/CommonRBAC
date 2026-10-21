package com.example.rbac.api.identity;

import java.util.Optional;

/**
 * Loads identity facts from the system of record.
 * The cache layer wraps this source; the guard never calls it directly.
 */
public interface IdentitySnapshotSource {

    Optional<IdentitySnapshot> load(long userId);
}
