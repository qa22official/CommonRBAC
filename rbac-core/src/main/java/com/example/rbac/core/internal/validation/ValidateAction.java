package com.example.rbac.core.internal.validation;

/** 要对数据做什么操作——让校验器只在特定场景触发。 */
public enum ValidateAction {
    CREATE, UPDATE, DELETE, DISABLE, ASSIGN_ROLES, ASSIGN_PERMS
}
