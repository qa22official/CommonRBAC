package com.example.rbac.api.identity;

import java.util.Optional;

/**
 * Reads identity facts for the guard. Implementations may serve from a local
 * cache; a missing user is represented by {@link Optional#empty()}.
 */
public interface IdentityQuery {

    Optional<IdentitySnapshot> findByUserId(long userId);
}
