package com.example.rbac.api.audit;

/**
 * 权限分类：决定权限码的使用场景。
 * 仅 API / SYSTEM 类可用于 @RequiresPermission。
 */
public enum PermissionCategory {
    /** 控制接口访问，真正的安全边界。可用于 @RequiresPermission。 */
    API,
    /** 控制前端菜单显隐；不得用于 @RequiresPermission（门禁拦截）。 */
    MENU,
    /** 控制前端按钮显隐；不得用于 @RequiresPermission（门禁拦截）。 */
    BUTTON,
    /** 框架级 / 系统级权限点，不直接暴露给业务使用者。 */
    SYSTEM
}
