package com.example.rbac.core.internal.validator;

/**
 * Read-only authorization rule. Implementations must not write to the database
 * so the chain can be executed repeatedly without side effects (05 §3.4).
 */
public interface ConstraintValidator {

    void validate(AuthorizationCommand command);
}
