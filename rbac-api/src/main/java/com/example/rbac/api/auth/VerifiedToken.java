package com.example.rbac.api.auth;

/**
 * Claims the guard needs after signature and issuer/audience validation.
 */
public record VerifiedToken(
        long userId,
        long tokenVersion,
        String username,
        String keyId) {
}
