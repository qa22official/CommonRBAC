package com.example.rbac.api.dto.user;

import java.util.Set;

public record UserRoles(Set<Long> roleIds, int version) {

    public UserRoles {
        roleIds = Set.copyOf(roleIds);
    }
}
