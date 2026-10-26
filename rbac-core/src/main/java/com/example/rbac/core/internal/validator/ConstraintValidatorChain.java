package com.example.rbac.core.internal.validator;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Single entry point for every authorization write (05 §3.4). Adding an RBAC2
 * rule only appends a validator; existing call sites stay unchanged.
 */
@Component
public class ConstraintValidatorChain {

    private final List<ConstraintValidator> validators;

    public ConstraintValidatorChain(List<ConstraintValidator> validators) {
        this.validators = List.copyOf(validators);
    }

    public void validate(AuthorizationCommand command) {
        for (ConstraintValidator validator : validators) {
            validator.validate(command);
        }
    }
}
