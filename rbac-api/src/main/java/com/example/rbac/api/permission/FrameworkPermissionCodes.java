package com.example.rbac.api.permission;

/**
 * 框架级权限码常量——不放在所有业务代码的公共变更点，
 * 只承载 rbac-api 自身需要的通配与框架前缀。
 * 业务级权限码（biz:*）放各业务模块自己的常量类。
 */
public final class FrameworkPermissionCodes {

    /** 超级管理员通配，匹配一切所需权限码。 */
    public static final String SUPER_ADMIN = "*";

    /** 框架前缀通配，匹配所有 sys: 开头的权限码。 */
    public static final String SYS_STAR = "sys:*";

    private FrameworkPermissionCodes() {
    }
}
