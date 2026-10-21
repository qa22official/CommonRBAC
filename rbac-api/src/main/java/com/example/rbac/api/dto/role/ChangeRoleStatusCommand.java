package com.example.rbac.api.dto.role;

public record ChangeRoleStatusCommand(
        long operatorId,
        long roleId,
        int status) {
}
