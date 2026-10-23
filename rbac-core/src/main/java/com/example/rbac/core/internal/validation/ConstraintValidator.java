package com.example.rbac.core.internal.validation;

/**
 * 单一校验器。只读：不得写数据库。
 */
public interface ConstraintValidator {

    /** 对校验目标 {@code target} 执行校验；失败抛 {@link ConstraintViolationException}。 */
    void validate(Object target, ValidateAction action);
}
