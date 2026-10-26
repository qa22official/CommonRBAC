package com.example.rbac.api.service;

import com.example.rbac.api.auth.AuthAccount;

import java.util.Optional;

/**
 * Authentication-side user operations implemented by {@code rbac-core}.
 */
public interface UserAuthService {

    Optional<AuthAccount> findForLogin(String username);

    int incrementTokenVersion(long userId);

    void recordLoginFailure(long userId);

    void recordLoginSuccess(long userId);

    /**
     * Clears a lock that has already expired, so the next failure starts a new
     * counting window instead of immediately re-locking the account.
     */
    void resetLoginFailures(long userId);

    /**
     * Updates the password hash, clears {@code must_change_password}, increments
     * {@code token_version}, and returns the new version.
     */
    int changePassword(long userId, String newPasswordHash);
}
