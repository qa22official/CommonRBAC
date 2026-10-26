package com.example.rbac.cache.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Redis Pub/Sub payload. Messages carry notification metadata only, never
 * permission data (02 §6).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CacheMessage(String type, long version, Long userId) {

    public static final String PERM_CHANGED = "PERM_CHANGED";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
}
