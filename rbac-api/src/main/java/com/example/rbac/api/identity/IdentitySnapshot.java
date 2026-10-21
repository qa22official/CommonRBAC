package com.example.rbac.api.identity;

import java.time.Instant;
import java.util.Objects;

/**
 * Per-user authentication state used by the guard.
 * {@code enabled} maps to {@code sys_user.status == 1}.
 */
public record IdentitySnapshot(
        long userId,
        long tokenVersion,
        boolean enabled,
        boolean mustChangePassword,
        Instant loadedAt,
        Instant expiresAt) {

    public IdentitySnapshot {
        Objects.requireNonNull(loadedAt, "loadedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
