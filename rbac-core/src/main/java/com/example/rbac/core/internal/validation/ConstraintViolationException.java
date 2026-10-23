package com.example.rbac.core.internal.validation;

/**
 * 校验器链的异常。实现约束校验器 MUST NOT 把业务异常透出本层。
 */
public class ConstraintViolationException extends RuntimeException {

    public ConstraintViolationException(String message) {
        super(message);
    }
}
