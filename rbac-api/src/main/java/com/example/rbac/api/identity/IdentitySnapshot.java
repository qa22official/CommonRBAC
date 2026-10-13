package com.example.rbac.api.identity;

import java.time.Instant;

public record IdentitySnapshot(
        long userId,
        long tokenVersion,
        boolean enabled,
        Instant loadedAt,
        Instant expiresAt) {
}
