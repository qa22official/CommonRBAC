package com.example.rbac.core.internal.validation;

import org.springframework.stereotype.Component;

/**
 * FR-G3：用户不能删除或停用自己。
 */
@Component
public class SelfProtectorValidator implements ConstraintValidator {

    private static final ThreadLocal<Long> OPERATOR = new ThreadLocal<>();

    public static void setOperator(long operatorId) { OPERATOR.set(operatorId); }
    public static void clearOperator() { OPERATOR.remove(); }
    public static long operator() { Long o = OPERATOR.get(); return o == null ? 0 : o; }

    @Override
    public void validate(Object target, ValidateAction action) {
        if (action != ValidateAction.DELETE && action != ValidateAction.DISABLE) return;
        if (!(target instanceof Long id)) return;
        if (id.equals(operator())) {
            throw new ConstraintViolationException("用户不能删除或停用自己");
        }
    }
}
