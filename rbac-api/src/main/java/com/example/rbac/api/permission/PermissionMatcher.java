package com.example.rbac.api.permission;

import java.util.Objects;
import java.util.Set;

/**
 * Single implementation of the permission-code matching rule shared by the
 * guard, the {@code /api/auth/permit} endpoint, and business modules.
 * <p>
 * Exact match first; wildcard prefixes are only consulted when the caller holds
 * a wildcard entry, so ordinary users pay a single hash lookup.
 */
public final class PermissionMatcher {

    private static final String ALL = "*";
    private static final char SEPARATOR = ':';

    private PermissionMatcher() {
    }

    public static boolean matches(Set<String> grantedCodes, String requiredCode) {
        Objects.requireNonNull(grantedCodes, "grantedCodes");
        Objects.requireNonNull(requiredCode, "requiredCode");
        if (grantedCodes.contains(requiredCode) || grantedCodes.contains(ALL)) {
            return true;
        }
        return matchesWildcard(grantedCodes, requiredCode);
    }

    private static boolean matchesWildcard(Set<String> grantedCodes, String requiredCode) {
        int index = requiredCode.lastIndexOf(SEPARATOR);
        while (index > 0) {
            if (grantedCodes.contains(requiredCode.substring(0, index) + SEPARATOR + ALL)) {
                return true;
            }
            index = requiredCode.lastIndexOf(SEPARATOR, index - 1);
        }
        return false;
    }
}
