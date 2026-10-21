package com.example.rbac.api.dto.role;

import java.util.Objects;

public record CreateRoleCommand(
        long operatorId,
        String code,
        String name,
        String description,
        Integer sort) {

    public CreateRoleCommand {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
