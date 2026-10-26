package com.example.rbac.api.dto.role;

import java.util.Objects;

public record UpdateRoleCommand(
        long operatorId,
        long roleId,
        String name,
        String description,
        Integer sort,
        Long inheritRoleId) {

    public UpdateRoleCommand {
        Objects.requireNonNull(name, "name");
    }
}
