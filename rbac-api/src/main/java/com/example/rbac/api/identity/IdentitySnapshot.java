package com.example.rbac.api.identity;

import java.time.Instant;
import java.util.Objects;

/** 身份快照：JWT 验签后用于比对 token_version 与启停状态。 */
public class IdentitySnapshot {

    private long userId;
    private long tokenVersion;
    private boolean enabled;
    private Instant loadedAt;
    private Instant expiresAt;

    public IdentitySnapshot() {}

    public IdentitySnapshot(long userId, long tokenVersion, boolean enabled,
                            Instant loadedAt, Instant expiresAt) {
        this.userId = userId;
        this.tokenVersion = tokenVersion;
        this.enabled = enabled;
        this.loadedAt = loadedAt;
        this.expiresAt = expiresAt;
    }

    public long userId() { return userId; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    public long tokenVersion() { return tokenVersion; }
    public long getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(long tokenVersion) { this.tokenVersion = tokenVersion; }

    public boolean enabled() { return enabled; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant loadedAt() { return loadedAt; }
    public Instant getLoadedAt() { return loadedAt; }
    public void setLoadedAt(Instant loadedAt) { this.loadedAt = loadedAt; }

    public Instant expiresAt() { return expiresAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
