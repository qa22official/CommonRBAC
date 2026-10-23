package com.example.rbac.admin.internal;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.cache.internal.CachePublisher;
import com.example.rbac.core.internal.domain.Role;
import com.example.rbac.core.internal.mapper.PermissionMapper;
import com.example.rbac.core.internal.mapper.RelMapper;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.validation.ConstraintValidatorChain;
import com.example.rbac.core.internal.validation.ValidateAction;

/**
 * 角色↔权限 的覆盖式授权保护点：FOR UPDATE + expectedVersion + 约束链 + 失效广播。
 */
@Service
public class RolePermService {

    private final RoleMapper roleMapper;
    private final RelMapper relMapper;
    private final PermissionMapper permMapper;
    private final AuditRecorder audit;
    private final CachePublisher publisher;
    private final ConstraintValidatorChain chain;

    public RolePermService(RoleMapper roleMapper, RelMapper relMapper, PermissionMapper permMapper,
                           AuditRecorder audit, CachePublisher publisher, ConstraintValidatorChain chain) {
        this.roleMapper = roleMapper;
        this.relMapper = relMapper;
        this.permMapper = permMapper;
        this.audit = audit;
        this.publisher = publisher;
        this.chain = chain;
    }

    @Transactional
    public RolePermResult assignPermissions(long roleId, long expectedVersion, Set<Long> newPermissionIds, long operatorId) {
        Role role = roleMapper.findByIdForUpdate(roleId).orElseThrow(() -> new IllegalArgumentException("role not found: " + roleId));
        if (role.permVersion() != expectedVersion) {
            throw new com.example.rbac.api.support.ConflictException();
        }

        // 构造权限码集合，供 SuperAdminImmutabilityValidator 检查
        var codes = new java.util.LinkedHashSet<String>();
        for (Long pid : newPermissionIds) {
            permMapper.findById(pid).ifPresent(p -> codes.add(p.code()));
        }
        chain.validate(codes, ValidateAction.ASSIGN_PERMS);

        String before = String.valueOf(relMapper.findPermissionIdsByRoleId(roleId));
        relMapper.clearRolePermissions(roleId);
        if (newPermissionIds != null && !newPermissionIds.isEmpty()) {
            relMapper.batchInsertRolePermissions(roleId, newPermissionIds);
        }
        roleMapper.bumpPermVersion(roleId);

        audit.record(new AuditRecord(AuditCategory.CHANGE, "ASSIGN_PERMS", "auth-service",
                operatorId, null, "ROLE", String.valueOf(roleId),
                before, String.valueOf(newPermissionIds), AuditRecord.RESULT_SUCCESS, null, null, null, null, null));

        publisher.publishPermChanged(roleId);

        return new RolePermResult(roleId, role.permVersion() + 1);
    }

    public record RolePermResult(long roleId, long newPermVersion) {}
}
