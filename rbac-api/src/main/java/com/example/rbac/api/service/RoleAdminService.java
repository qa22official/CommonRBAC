package com.example.rbac.api.service;

import com.example.rbac.api.dto.PageResult;
import com.example.rbac.api.dto.RoleSummary;
import com.example.rbac.api.dto.role.AssignPermissionsCommand;
import com.example.rbac.api.dto.role.ChangeRoleStatusCommand;
import com.example.rbac.api.dto.role.CreateRoleCommand;
import com.example.rbac.api.dto.role.RoleDetail;
import com.example.rbac.api.dto.role.RoleListItem;
import com.example.rbac.api.dto.role.RolePermissions;
import com.example.rbac.api.dto.role.RoleQuery;
import com.example.rbac.api.dto.role.RoleTreeNode;
import com.example.rbac.api.dto.role.UpdateRoleCommand;

import java.util.List;
import java.util.Optional;

/**
 * Role administration contract implemented by {@code rbac-core}.
 */
public interface RoleAdminService {

    PageResult<RoleListItem> list(RoleQuery query);

    List<RoleSummary> options();

    List<RoleTreeNode> tree();

    Optional<RoleDetail> findDetail(long roleId);

    long create(CreateRoleCommand command);

    void update(UpdateRoleCommand command);

    void delete(long operatorId, long roleId);

    void changeStatus(ChangeRoleStatusCommand command);

    Optional<RolePermissions> findPermissions(long roleId);

    void assignPermissions(AssignPermissionsCommand command);
}
