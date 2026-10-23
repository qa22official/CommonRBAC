package com.example.rbac.core.internal.domain;

import java.time.Instant;

/** 领域对象：角色。status 1=启用 2=停用 3=删除。 */
public class Role {

    private Long id;
    private String code;
    private String name;
    private String description;
    private int status;
    private long permVersion;
    private long inheritRoleId;   // RBAC0 恒为 0；RBAC1 启用后沿此链收集
    private boolean builtin;
    private Instant createdAt;
    private Instant updatedAt;

    public Role() {}

    public Role(Long id, String code, String name, String description, int status,
                long permVersion, long inheritRoleId, boolean builtin,
                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.status = status;
        this.permVersion = permVersion;
        this.inheritRoleId = inheritRoleId;
        this.builtin = builtin;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean enabled() { return status == 1; }

    public Long id() { return id; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String code() { return code; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String name() { return name; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String description() { return description; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int status() { return status; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public long permVersion() { return permVersion; }
    public long getPermVersion() { return permVersion; }
    public void setPermVersion(long permVersion) { this.permVersion = permVersion; }

    public long inheritRoleId() { return inheritRoleId; }
    public long getInheritRoleId() { return inheritRoleId; }
    public void setInheritRoleId(long inheritRoleId) { this.inheritRoleId = inheritRoleId; }

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
