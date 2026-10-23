package com.example.rbac.core.internal.permission;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.rbac.api.permission.PermissionResolver;
import com.example.rbac.core.internal.domain.Permission;
import com.example.rbac.core.internal.domain.Role;
import com.example.rbac.core.internal.mapper.PermissionMapper;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.mapper.RelMapper;
import org.springframework.stereotype.Component;

/**
 * {@link PermissionResolver} 的本地内存版本。
 * <p>
 * 实现口径（与 design D-1 一致）：
 * <ol>
 *   <li>把 userId 绑定的全部角色拉出来</li>
 *   <li>过滤停用角色（{@code status == 1}）</li>
 *   <li>把这些角色绑定的全部权限拉出来，过滤停用权限</li>
 *   <li>返回不可变 Set（不展开通配；通配匹配在鉴权链路执行时由拦截器用 WildcardMatcher 判定）</li>
 * </ol>
 * 主链路（鉴权过滤器 → PermissionInterceptor）只读本地缓存，不查本实现；本实现仅在缓存未命中时被 rbac-cache 调用。
 */
@Component
public class PermissionResolverImpl implements PermissionResolver {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RelMapper relMapper;

    public PermissionResolverImpl(RoleMapper roleMapper, PermissionMapper permissionMapper, RelMapper relMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.relMapper = relMapper;
    }

    @Override
    public Set<String> resolve(long userId) {
        List<Role> roles = roleMapper.findByUserId(userId);
        Set<Long> enabledRoleIds = new LinkedHashSet<>();
        for (Role r : roles) {
            if (r.enabled()) enabledRoleIds.add(r.id());
        }
        if (enabledRoleIds.isEmpty()) return Collections.emptySet();

        Set<String> codes = new LinkedHashSet<>();
        for (Long rid : enabledRoleIds) {
            List<Permission> perms = permissionMapper.findByRoleId(rid);
            for (Permission p : perms) {
                if (p.enabled()) codes.add(p.code());
            }
        }
        return Collections.unmodifiableSet(codes);
    }

    /** rbac-cache 缓存 A 的加载入口：roleId → 过滤停用权限后的权限码集合。 */
    public Set<String> resolveRoleCodes(long roleId) {
        List<Permission> perms = permissionMapper.findByRoleId(roleId);
        Set<String> codes = new LinkedHashSet<>();
        for (Permission p : perms) {
            if (p.enabled()) codes.add(p.code());
        }
        return Collections.unmodifiableSet(codes);
    }

    /** rbac-cache 缓存 B 的加载入口：userId → 过滤停用角色后的角色 ID 集合。 */
    public Set<Long> resolveUserRoles(long userId) {
        List<Role> roles = roleMapper.findByUserId(userId);
        Set<Long> ids = new LinkedHashSet<>();
        for (Role r : roles) {
            if (r.enabled()) ids.add(r.id());
        }
        return Collections.unmodifiableSet(ids);
    }
}
