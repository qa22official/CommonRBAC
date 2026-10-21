package com.example.rbac.api.permission;

import java.util.Set;

/**
 * Stable contract for resolving a user's effective permission codes.
 * Returns an immutable set; never null; never throws unrelated exceptions.
 */
public interface PermissionResolver {

    Set<String> resolve(long userId);
}
