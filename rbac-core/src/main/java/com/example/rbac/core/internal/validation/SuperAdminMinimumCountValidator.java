package com.example.rbac.core.internal.validation;

import org.springframework.stereotype.Component;

import com.example.rbac.core.internal.domain.Role;
import com.example.rbac.core.internal.mapper.RoleMapper;

/**
 * FR-G2：系统必须保留至少 1 个启用的 SUPER_ADMIN。
 * 触发时机：删除 / 停用 SUPER_ADMIN 角色时，若当前只剩 1 个启用的则拒绝。
 */
@Component
public class SuperAdminMinimumCountValidator implements ConstraintValidator {

    private final RoleMapper roleMapper;

    public SuperAdminMinimumCountValidator(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public void validate(Object target, ValidateAction action) {
        if (action != ValidateAction.DELETE && action != ValidateAction.DISABLE) return;
        if (!(target instanceof Role role)) return;
        if (!"SUPER_ADMIN".equals(role.code())) return;

        int count = roleMapper.countEnabledSuperAdmin();
        if (count <= 1) {
            throw new ConstraintViolationException("系统必须至少保留 1 个启用的 SUPER_ADMIN");
        }
    }
}
