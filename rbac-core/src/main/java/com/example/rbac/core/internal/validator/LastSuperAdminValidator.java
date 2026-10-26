package com.example.rbac.core.internal.validator;

import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.core.internal.domain.RoleEntity;
import com.example.rbac.core.internal.domain.UserEntity;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.mapper.UserMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * FR-G2: the system must keep at least one enabled super administrator.
 */
@Component
@Order(10)
public class LastSuperAdminValidator implements ConstraintValidator {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public LastSuperAdminValidator(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public void validate(AuthorizationCommand command) {
        TargetTypeSupport.require(command);
        if (command.targetType() != AuthorizationCommand.TargetType.USER_STATUS
                && command.targetType() != AuthorizationCommand.TargetType.USER_ROLES) {
            return;
        }
        UserEntity target = userMapper.findById(command.targetUserId());
        if (target == null || target.getStatus() == null || target.getStatus() != 1) {
            return;
        }
        if (userMapper.countByRoleCode(command.targetUserId(), "SUPER_ADMIN") == 0) {
            return;
        }
        // Serialize every "last super administrator" check on the stable
        // SUPER_ADMIN role row. Locking only the target user allowed two
        // administrators to disable each other concurrently: both read two
        // enabled super admins and both commit, leaving zero.
        RoleEntity superRole = roleMapper.findByCode("SUPER_ADMIN");
        if (superRole != null) {
            roleMapper.findByIdForUpdate(superRole.getId());
        }
        boolean staysSuperAdmin;
        if (command.targetType() == AuthorizationCommand.TargetType.USER_ROLES) {
            staysSuperAdmin = superRole != null && superRole.enabled()
                    && command.finalRoleIds().contains(superRole.getId());
        } else {
            staysSuperAdmin = command.targetStatus() != null && command.targetStatus() == 1;
        }
        if (!staysSuperAdmin && userMapper.countEnabledSuperAdmins() <= 1) {
            throw new BusinessException(ErrorCodes.LAST_SUPER_ADMIN_FORBIDDEN,
                    "系统必须保留至少一个启用的超级管理员");
        }
    }
}
