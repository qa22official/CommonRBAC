package com.example.rbac.api.service;

import com.example.rbac.api.dto.permission.CreatePermissionCommand;
import com.example.rbac.api.dto.permission.PermissionDetail;
import com.example.rbac.api.dto.permission.PermissionTreeNode;
import com.example.rbac.api.dto.permission.UpdatePermissionCommand;

import java.util.List;
import java.util.Optional;

/**
 * Permission administration contract implemented by {@code rbac-core}.
 */
public interface PermissionAdminService {

    List<PermissionTreeNode> tree();

    Optional<PermissionDetail> findDetail(long permissionId);

    long create(CreatePermissionCommand command);

    void update(UpdatePermissionCommand command);

    void delete(long operatorId, long permissionId);
}
