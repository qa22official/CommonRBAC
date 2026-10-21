package com.example.rbac.api.security;

import java.util.Optional;

/**
 * Password strength policy contract.
 */
public interface PasswordPolicy {

    /**
     * @return empty when the password is valid; otherwise a user-facing
     *         violation message
     */
    Optional<String> violation(String username, String rawPassword);
}
