package com.example.rbac.api.identity;

import java.util.Optional;

public interface IdentityQuery {

    Optional<IdentitySnapshot> findByUserId(long userId);
}
