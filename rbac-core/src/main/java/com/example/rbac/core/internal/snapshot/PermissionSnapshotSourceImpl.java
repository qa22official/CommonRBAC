package com.example.rbac.core.internal.snapshot;

import com.example.rbac.api.permission.PermissionSnapshot;
import com.example.rbac.api.permission.PermissionSnapshotSource;
import com.example.rbac.core.internal.domain.PermissionEntity;
import com.example.rbac.core.internal.domain.RoleEntity;
import com.example.rbac.core.internal.domain.RolePermissionRow;
import com.example.rbac.core.internal.domain.UserRoleRow;
import com.example.rbac.core.internal.mapper.PermissionMapper;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.mapper.RolePermissionMapper;
import com.example.rbac.core.internal.mapper.UserRoleMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads all permission facts in one pass (03 §5.1). The cache layer wraps this
 * call with single-flight replacement; this class only performs the three
 * table scans required by the design.
 */
@Component
public class PermissionSnapshotSourceImpl implements PermissionSnapshotSource {

    public static final int SOFT_TTL_SECONDS = 30;

    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    public PermissionSnapshotSourceImpl(UserRoleMapper userRoleMapper,
                                        RolePermissionMapper rolePermissionMapper,
                                        RoleMapper roleMapper,
                                        PermissionMapper permissionMapper) {
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    @Transactional
    public PermissionSnapshot load() {
        Map<Long, Set<String>> rolePermissions = new HashMap<>();
        for (RolePermissionRow row : rolePermissionMapper.findAllEnabled()) {
            rolePermissions.computeIfAbsent(row.getRoleId(), key -> new HashSet<>())
                    .add(row.getPermissionCode());
        }
        Map<Long, Set<Long>> userRoles = new HashMap<>();
        for (UserRoleRow row : userRoleMapper.findAll()) {
            userRoles.computeIfAbsent(row.getUserId(), key -> new HashSet<>())
                    .add(row.getRoleId());
        }
        Map<Long, PermissionSnapshot.RoleMeta> roleMeta = new HashMap<>();
        for (RoleEntity role : roleMapper.findAll()) {
            roleMeta.put(role.getId(), new PermissionSnapshot.RoleMeta(
                    role.enabled(),
                    role.getInheritRoleId() == null ? 0L : role.getInheritRoleId()));
        }
        Map<String, Long> permissionIds = new HashMap<>();
        for (PermissionEntity permission : permissionMapper.findAllEnabled()) {
            permissionIds.put(permission.getCode(), permission.getId());
        }
        Instant now = Instant.now();
        return new PermissionSnapshot(
                rolePermissions,
                userRoles,
                roleMeta,
                permissionIds,
                0L,
                now,
                now.plusSeconds(SOFT_TTL_SECONDS),
                PermissionSnapshot.SnapshotState.VALID);
    }
}
