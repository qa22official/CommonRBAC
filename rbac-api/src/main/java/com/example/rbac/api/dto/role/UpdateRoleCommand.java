package com.example.rbac.api.dto.role;

import java.util.Objects;

public record UpdateRoleCommand(
        long operatorId,
        long roleId,
        String name,
        String description,
        Integer sort) {

    public UpdateRoleCommand {
        Objects.requireNonNull(name, "name");
    }
}
