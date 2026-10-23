package com.example.rbac.core.internal.permission;

import com.example.rbac.api.permission.FrameworkPermissionCodes;

/**
 * 通配权限码匹配。规则（docs/02 §10 + design D-1）：
 * <ul>
 *   <li>{@code "*"} 匹配一切所需权限码</li>
 *   <li>{@code "X:*"} 匹配所有 {@code "X:"} 前缀的所需权限码</li>
 *   <li>精确权限码只能精确匹配；不会把 {@code "sys:user:*"} 视为通配展开</li>
 * </ul>
 * 匹配口径"单次 contains 调用内全部可能的命中"由 PermissionResolverImpl 负责；本类只判布尔。
 */
public final class WildcardMatcher {

    private WildcardMatcher() {
    }

    public static boolean matches(String owned, String required) {
        if (owned == null || required == null) return false;
        if (FrameworkPermissionCodes.SUPER_ADMIN.equals(owned)) return true;
        if (owned.endsWith(":*")) {
            String prefix = owned.substring(0, owned.length() - 1); // e.g. "sys:"
            return required.startsWith(prefix);
        }
        return owned.equals(required);
    }
}
