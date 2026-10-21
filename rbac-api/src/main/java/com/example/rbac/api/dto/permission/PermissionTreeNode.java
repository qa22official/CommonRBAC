package com.example.rbac.api.dto.permission;

import java.util.List;

public record PermissionTreeNode(
        long id,
        String code,
        String name,
        String resourceType,
        Long parentId,
        String path,
        String httpMethod,
        String icon,
        int status,
        int sort,
        List<PermissionTreeNode> children) {

    public PermissionTreeNode {
        children = List.copyOf(children);
    }
}
