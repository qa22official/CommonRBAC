package com.example.rbac.core.internal.domain;

import java.time.Instant;

import com.example.rbac.api.audit.PermissionCategory;

/** 领域对象：权限点。status 1=启用 2=停用 3=删除。 */
public class Permission {

    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private PermissionCategory category;
    private int status;
    private int sort;
    private Instant createdAt;
    private Instant updatedAt;

    public Permission() {}

    public Permission(Long id, String code, String name, Long parentId,
                      PermissionCategory category, int status, int sort,
                      Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.parentId = parentId;
        this.category = category;
        this.status = status;
        this.sort = sort;
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

    public Long parentId() { return parentId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public PermissionCategory category() { return category; }
    public PermissionCategory getCategory() { return category; }
    public void setCategory(PermissionCategory category) { this.category = category; }

    public int status() { return status; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public int sort() { return sort; }
    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }

    public Instant createdAt() { return createdAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant updatedAt() { return updatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
