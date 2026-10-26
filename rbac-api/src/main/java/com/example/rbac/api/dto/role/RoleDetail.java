package com.example.rbac.api.dto.role;

public record RoleDetail(
        long id,
        String code,
        String name,
        String description,
        int status,
        boolean builtin,
        int sort,
        int permVersion,
        long userCount,
        long permissionCount,
        Long inheritRoleId,
        String inheritRoleName) {
}
