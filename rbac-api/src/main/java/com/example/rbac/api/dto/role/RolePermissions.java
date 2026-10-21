package com.example.rbac.api.dto.role;

import java.util.Set;

public record RolePermissions(Set<String> permissionCodes, int version) {

    public RolePermissions {
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
