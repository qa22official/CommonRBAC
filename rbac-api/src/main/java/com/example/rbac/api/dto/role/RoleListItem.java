package com.example.rbac.api.dto.role;

public record RoleListItem(
        long id,
        String code,
        String name,
        String description,
        int status,
        boolean builtin,
        int sort,
        Long inheritRoleId,
        String inheritRoleName) {
}
