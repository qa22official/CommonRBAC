package com.example.rbac.api.permission;

import java.util.regex.Pattern;

/**
 * The single permission-code shape used by the admin API and the external
 * service endpoint. Keeping it in the contract module means core, auth and
 * tests cannot drift apart.
 */
public final class PermissionCodeFormat {

    private static final Pattern CODE = Pattern.compile(
            "^(\\*|[a-z][a-z0-9-]*(?::(?:\\*|[a-z][a-z0-9-]*)(?:\\.[a-z][a-z0-9-]*)?){0,2})$");

    private PermissionCodeFormat() {
    }

    public static boolean isValid(String code) {
        return code != null && code.length() <= 128 && CODE.matcher(code).matches();
    }
}
