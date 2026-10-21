package com.example.rbac.api.dto.user;

import java.util.Set;

public record AssignRolesCommand(
        long operatorId,
        long userId,
        Set<Long> roleIds,
        int expectedVersion) {

    public AssignRolesCommand {
        roleIds = Set.copyOf(roleIds);
    }
}
