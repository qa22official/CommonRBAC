package com.example.rbac.api.dto.role;

import java.util.List;

/**
 * Role view organized by {@code inherit_role_id}. A node appears under the role
 * it inherits from; the data direction is unchanged (05 §4.6/§4.7).
 */
public record RoleTreeNode(
        long id,
        String code,
        String name,
        String description,
        int status,
        boolean builtin,
        int sort,
        Long inheritRoleId,
        String inheritRoleName,
        List<RoleTreeNode> children) {

    public RoleTreeNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
