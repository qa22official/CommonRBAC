package com.example.rbac.api.auth;

import java.time.Instant;
import java.util.Objects;

/**
 * Authentication facts required by the login flow.
 * {@code status} uses the same values as {@code sys_user.status}.
 */
public record AuthAccount(
        long id,
        String username,
        String nickname,
        String passwordHash,
        int status,
        boolean mustChangePassword,
        int tokenVersion,
        Instant lockedUntil) {

    public AuthAccount {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
    }
}
