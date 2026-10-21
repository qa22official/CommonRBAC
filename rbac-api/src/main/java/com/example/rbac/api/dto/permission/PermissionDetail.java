package com.example.rbac.api.dto.permission;

public record PermissionDetail(
        long id,
        String code,
        String name,
        String resourceType,
        Long parentId,
        String path,
        String httpMethod,
        String icon,
        int status,
        int sort,
        boolean builtin) {
}
