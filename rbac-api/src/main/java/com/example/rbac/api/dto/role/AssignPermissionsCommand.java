package com.example.rbac.api.dto.role;

import java.util.Set;

public record AssignPermissionsCommand(
        long operatorId,
        long roleId,
        Set<String> permissionCodes,
        int expectedVersion) {

    public AssignPermissionsCommand {
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
