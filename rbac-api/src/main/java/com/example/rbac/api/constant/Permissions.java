package com.example.rbac.api.constant;

/**
 * Framework-level permission codes.
 * Business modules must define their own {@code biz:*} constants.
 */
public final class Permissions {

    public static final String ALL = "*";

    public static final String SYS_USER = "sys:user";
    public static final String SYS_USER_ALL = "sys:user:*";
    public static final String SYS_USER_READ = "sys:user:read";
    public static final String SYS_USER_CREATE = "sys:user:create";
    public static final String SYS_USER_UPDATE = "sys:user:update";
    public static final String SYS_USER_DELETE = "sys:user:delete";
    public static final String SYS_USER_TOGGLE = "sys:user:toggle";
    public static final String SYS_USER_RESET_PASSWORD = "sys:user:reset-password";
    public static final String SYS_USER_ASSIGN_ROLE = "sys:user:assign-role";

    public static final String SYS_ROLE = "sys:role";
    public static final String SYS_ROLE_ALL = "sys:role:*";
    public static final String SYS_ROLE_READ = "sys:role:read";
    public static final String SYS_ROLE_CREATE = "sys:role:create";
    public static final String SYS_ROLE_UPDATE = "sys:role:update";
    public static final String SYS_ROLE_DELETE = "sys:role:delete";
    public static final String SYS_ROLE_TOGGLE = "sys:role:toggle";
    public static final String SYS_ROLE_ASSIGN_PERM = "sys:role:assign-perm";

    public static final String SYS_PERM = "sys:perm";
    public static final String SYS_PERM_ALL = "sys:perm:*";
    public static final String SYS_PERM_READ = "sys:perm:read";
    public static final String SYS_PERM_CREATE = "sys:perm:create";
    public static final String SYS_PERM_UPDATE = "sys:perm:update";
    public static final String SYS_PERM_DELETE = "sys:perm:delete";

    public static final String SYS_AUDIT = "sys:audit";
    public static final String SYS_AUDIT_READ = "sys:audit:read";

    private Permissions() {
    }
}
