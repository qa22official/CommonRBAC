package com.example.rbac.core.internal.domain;

import java.time.Instant;

/** 领域对象：用户。status 1=启用 2=停用 3=删除。 */
public class User {

    private Long id;
    private String username;
    private String nickname;
    private String passwordHash;
    private long tokenVersion;
    private int status;
    private long roleVersion;
    private boolean builtin;
    private Instant createdAt;
    private Instant updatedAt;

    public User() {}

    public User(Long id, String username, String nickname, String passwordHash,
                long tokenVersion, int status, long roleVersion, boolean builtin,
                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.tokenVersion = tokenVersion;
        this.status = status;
        this.roleVersion = roleVersion;
        this.builtin = builtin;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long id() { return id; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String username() { return username; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String nickname() { return nickname; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String passwordHash() { return passwordHash; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public long tokenVersion() { return tokenVersion; }
    public long getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(long tokenVersion) { this.tokenVersion = tokenVersion; }

    public int status() { return status; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public long roleVersion() { return roleVersion; }
    public long getRoleVersion() { return roleVersion; }
    public void setRoleVersion(long roleVersion) { this.roleVersion = roleVersion; }

    public boolean builtin() { return builtin; }
    public boolean isBuiltin() { return builtin; }
    public void setBuiltin(boolean builtin) { this.builtin = builtin; }

    public Instant createdAt() { return createdAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant updatedAt() { return updatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
