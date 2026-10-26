package com.example.rbac.api.dto.permission;

import java.util.Objects;

public record UpdatePermissionCommand(
        long operatorId,
        long permissionId,
        String name,
        String resourceType,
        Long parentId,
        String path,
        String httpMethod,
        String icon,
        Integer sort,
        Integer status,
        String code) {

    public UpdatePermissionCommand {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resourceType, "resourceType");
    }
}
