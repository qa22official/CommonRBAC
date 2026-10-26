package com.example.rbac.core.internal.support;

import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.core.internal.domain.RoleEntity;
import com.example.rbac.core.internal.mapper.RoleMapper;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates {@code inherit_role_id} edits. The runtime resolver is defensive too
 * (dirty data may predate these checks), but write operations must reject cycles
 * and over-deep chains with explicit errors (05 §4.6).
 */
@Component
public class RoleInheritanceGuard {

    public static final int MAX_DEPTH = 5;

    private final RoleMapper roleMapper;

    public RoleInheritanceGuard(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    public long validate(long roleId, Long requestedInheritRoleId) {
        if (requestedInheritRoleId == null || requestedInheritRoleId == 0L) {
            return 0L;
        }
        if (requestedInheritRoleId == roleId) {
            throw new BusinessException(ErrorCodes.INHERIT_CYCLE, "角色不能继承自身");
        }
        RoleEntity target = roleMapper.findById(requestedInheritRoleId);
        if (target == null) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "继承来源角色不存在");
        }
        if (!target.enabled()) {
            throw new BusinessException(ErrorCodes.INHERIT_ROLE_DISABLED, "选择的继承来源角色已停用");
        }
        int edges = 1;
        long cursor = target.getId();
        Set<Long> visited = new HashSet<>();
        while (cursor != 0L) {
            if (!visited.add(cursor)) {
                throw new BusinessException(ErrorCodes.INHERIT_CYCLE, "继承关系存在环");
            }
            if (cursor == roleId) {
                throw new BusinessException(ErrorCodes.INHERIT_CYCLE, "设置继承来源会形成环");
            }
            RoleEntity node = roleMapper.findById(cursor);
            if (node == null) {
                break;
            }
            long next = node.getInheritRoleId() == null ? 0L : node.getInheritRoleId();
            if (next == 0L) {
                break;
            }
            edges++;
            if (edges > MAX_DEPTH) {
                throw new BusinessException(ErrorCodes.INHERIT_DEPTH_EXCEEDED,
                        "继承链深度不能超过 " + MAX_DEPTH + " 层");
            }
            cursor = next;
        }
        return requestedInheritRoleId;
    }
}
