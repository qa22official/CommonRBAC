package com.example.rbac.core.internal.validation;

import java.util.Collection;

import org.springframework.stereotype.Component;

/**
 * FR-G4：用户不能移除自己最后一个管理类角色。
 * "管理类"判定：角色 code 以 {@code ADMIN} 结尾（SUPER_ADMIN、AUDITOR 都是）。
 * 严格实现需要查询当前操作人剩余角色；这里保留接口，最终判定由管理端 Service 组装。
 */
@Component
public class RoleAssignmentValidator implements ConstraintValidator {

    @Override
    public void validate(Object target, ValidateAction action) {
        if (action != ValidateAction.ASSIGN_ROLES) return;
        if (!(target instanceof Collection<?> newRoleIds)) return;
        // 简化版校验：若新角色列表为空则允许（语义上"全部移除"）——实际 FR-G4 需要比较"是否含最后一个 ADMIN 角色"。
        // 完整实现由 rbac-admin 的用户服务层在替换角色时先查当前操作人已拥有的 ADMIN 角色数量后再调用本链。
    }
}
