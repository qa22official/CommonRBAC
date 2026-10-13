package com.example.rbac.api.permission;

import java.util.Set;

/**
 * Stable contract for resolving a user's effective permission codes.
 */
public interface PermissionResolver {

    Set<String> resolve(long userId);
}
