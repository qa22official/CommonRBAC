package com.example.rbac.core.internal.validator;

import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.core.internal.mapper.RolePermissionMapper;
import com.example.rbac.core.internal.mapper.UserRoleMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * FR-G3 / FR-G4: operators cannot delete or disable themselves, and cannot
 * remove their own last management role. A role is "management" when it carries
 * any {@code sys:*} or {@code *} permission; no role name is consulted.
 */
@Component
@Order(5)
public class SelfLockoutValidator implements ConstraintValidator {

    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public SelfLockoutValidator(UserRoleMapper userRoleMapper, RolePermissionMapper rolePermissionMapper) {
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public void validate(AuthorizationCommand command) {
        TargetTypeSupport.require(command);
        if (command.operatorId() != command.targetUserId()) {
            return;
        }
        if (command.targetType() == AuthorizationCommand.TargetType.USER_STATUS) {
            if (command.targetStatus() != null && command.targetStatus() != 1) {
                throw new BusinessException(ErrorCodes.SELF_OPERATION_FORBIDDEN,
                        "不能停用或删除当前登录账号");
            }
            return;
        }
        if (command.targetType() != AuthorizationCommand.TargetType.USER_ROLES) {
            return;
        }
        List<Long> currentRoles = userRoleMapper.findRoleIdsByUserId(command.targetUserId());
        boolean currentlyManagement = hasManagementRole(currentRoles);
        boolean stillManagement = hasManagementRole(command.finalRoleIds());
        if (currentlyManagement && !stillManagement) {
            throw new BusinessException(ErrorCodes.LAST_ADMIN_ROLE_FORBIDDEN,
                    "不能移除自己的最后一个管理角色");
        }
    }

    private boolean hasManagementRole(Set<Long> roleIds) {
        for (Long roleId : roleIds) {
            if (isManagementRole(roleId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasManagementRole(List<Long> roleIds) {
        return hasManagementRole(Set.copyOf(roleIds));
    }

    private boolean isManagementRole(long roleId) {
        for (String code : rolePermissionMapper.findCodesByRoleId(roleId)) {
            if ("*".equals(code) || code.startsWith("sys:")) {
                return true;
            }
        }
        return false;
    }
}
