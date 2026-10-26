package com.example.rbac.api.identity;

/**
 * Authenticated principal stored in the request context. {@code username} is
 * carried in the token so audit records can name the operator without a
 * database lookup in the guard path.
 */
public record CurrentUser(long userId, long tokenVersion, String username) {
}
