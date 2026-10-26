package com.example.rbac.core.internal.validator;

import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.core.internal.domain.RoleEntity;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.mapper.RolePermissionMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FR-G1: built-in roles cannot be deleted or disabled, and the {@code *}
 * permission cannot be removed from their permission set.
 */
@Component
@Order(30)
public class BuiltinRoleProtectionValidator implements ConstraintValidator {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public BuiltinRoleProtectionValidator(RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public void validate(AuthorizationCommand command) {
        TargetTypeSupport.require(command);
        if (command.targetType() == AuthorizationCommand.TargetType.ROLE_DELETE
                || command.targetType() == AuthorizationCommand.TargetType.ROLE_STATUS) {
            RoleEntity role = roleMapper.findById(command.targetRoleId());
            if (role != null && role.builtin()
                    && (command.targetType() == AuthorizationCommand.TargetType.ROLE_DELETE
                    || (command.targetStatus() != null && command.targetStatus() != 1))) {
                throw new BusinessException(ErrorCodes.BUILTIN_ROLE_FORBIDDEN,
                        "内置角色不允许此操作");
            }
            return;
        }
        if (command.targetType() == AuthorizationCommand.TargetType.ROLE_PERMISSIONS) {
            RoleEntity role = roleMapper.findById(command.targetRoleId());
            if (role == null || !role.builtin()) {
                return;
            }
            List<String> current = rolePermissionMapper.findCodesByRoleId(command.targetRoleId());
            if (current.contains("*") && !command.finalPermissionCodes().contains("*")) {
                throw new BusinessException(ErrorCodes.BUILTIN_ROLE_ALL_PERMISSION_FORBIDDEN,
                        "不允许修改内置角色的全部权限");
            }
        }
    }
}
