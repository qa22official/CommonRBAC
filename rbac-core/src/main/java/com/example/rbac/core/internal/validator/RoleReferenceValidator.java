package com.example.rbac.core.internal.validator;

import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.mapper.UserRoleMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * FR-R4: a role referenced by users (or by another role's inheritance source)
 * cannot be deleted.
 */
@Component
@Order(40)
public class RoleReferenceValidator implements ConstraintValidator {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public RoleReferenceValidator(UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public void validate(AuthorizationCommand command) {
        TargetTypeSupport.require(command);
        if (command.targetType() != AuthorizationCommand.TargetType.ROLE_DELETE) {
            return;
        }
        long users = userRoleMapper.countByRoleId(command.targetRoleId());
        if (users > 0) {
            throw new BusinessException(ErrorCodes.ROLE_REFERENCED,
                    "该角色仍被 " + users + " 个用户绑定，无法删除");
        }
        long children = roleMapper.countByInheritRoleId(command.targetRoleId());
        if (children > 0) {
            throw new BusinessException(ErrorCodes.ROLE_REFERENCED,
                    "该角色仍被 " + children + " 个角色继承，无法删除");
        }
    }
}
