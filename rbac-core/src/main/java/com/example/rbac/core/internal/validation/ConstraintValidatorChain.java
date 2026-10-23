package com.example.rbac.core.internal.validation;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 校验器链。执行顺序：superAdminImmutability → superAdminMinimumCount → selfProtector → roleAssignmentValidator。
 * 链内任何一个 validator 抛异常即终止。校验器本身只读，不写数据库。
 */
@Component
public class ConstraintValidatorChain {

    private final List<ConstraintValidator> validators;

    public ConstraintValidatorChain(List<ConstraintValidator> validators) {
        this.validators = List.copyOf(validators);
    }

    public void validate(Object target, ValidateAction action) {
        for (ConstraintValidator v : validators) {
            v.validate(target, action);
        }
    }
}
