package com.example.rbac.core.internal.domain;

import java.time.LocalDateTime;

public class RoleEntity {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Long inheritRoleId;
    private Integer status;
    private Integer isBuiltin;
    private Integer permVersion;
    private Integer sort;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private String inheritRoleName;
    private Long userCount;
    private Long permissionCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getInheritRoleId() {
        return inheritRoleId;
    }

    public void setInheritRoleId(Long inheritRoleId) {
        this.inheritRoleId = inheritRoleId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsBuiltin() {
        return isBuiltin;
    }

    public void setIsBuiltin(Integer isBuiltin) {
        this.isBuiltin = isBuiltin;
    }

    public Integer getPermVersion() {
        return permVersion;
    }

    public void setPermVersion(Integer permVersion) {
        this.permVersion = permVersion;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getInheritRoleName() {
        return inheritRoleName;
    }

    public void setInheritRoleName(String inheritRoleName) {
        this.inheritRoleName = inheritRoleName;
    }

    public Long getUserCount() {
        return userCount;
    }

    public void setUserCount(Long userCount) {
        this.userCount = userCount;
    }

    public Long getPermissionCount() {
        return permissionCount;
    }

    public void setPermissionCount(Long permissionCount) {
        this.permissionCount = permissionCount;
    }

    public boolean builtin() {
        return isBuiltin != null && isBuiltin == 1;
    }

    public boolean enabled() {
        return status != null && status == 1;
    }
}
