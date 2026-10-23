package com.example.rbac.core.internal.validation;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.rbac.api.permission.FrameworkPermissionCodes;
import com.example.rbac.core.internal.domain.Role;

/**
 * FR-G1：SUPER_ADMIN 不可删除、不可停用、权限集合不可移除 {@code *}。
 */
@Component
public class SuperAdminImmutabilityValidator implements ConstraintValidator {

    @Override
    public void validate(Object target, ValidateAction action) {
        if (target instanceof Role r && "SUPER_ADMIN".equals(r.code())) {
            if (action == ValidateAction.DELETE || action == ValidateAction.DISABLE) {
                throw new ConstraintViolationException("内置角色 SUPER_ADMIN 不可删除或停用");
            }
        }
        if (target instanceof Set<?> codes) {
            if (action == ValidateAction.ASSIGN_PERMS && codes.stream().noneMatch(c -> FrameworkPermissionCodes.SUPER_ADMIN.equals(c))) {
                throw new ConstraintViolationException("SUPER_ADMIN 的权限集合不可移除 *");
            }
        }
    }
}
