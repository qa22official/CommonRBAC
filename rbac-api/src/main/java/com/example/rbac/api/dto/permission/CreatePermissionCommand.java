package com.example.rbac.api.dto.permission;

import java.util.Objects;

public record CreatePermissionCommand(
        long operatorId,
        String code,
        String name,
        String resourceType,
        Long parentId,
        String path,
        String httpMethod,
        String icon,
        Integer sort) {

    public CreatePermissionCommand {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resourceType, "resourceType");
    }
}
