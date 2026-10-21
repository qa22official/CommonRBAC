package com.example.rbac.api.support;

/**
 * Canonical business error codes.
 * HTTP status is derived from the code prefix; the client must use {@code code}
 * for business semantics.
 */
public final class ErrorCodes {

    public static final int SUCCESS = 0;

    public static final int PARAM_INVALID = 40000;
    public static final int PASSWORD_WEAK = 40010;
    public static final int PERMISSION_CODE_INVALID = 40020;

    public static final int TOKEN_INVALID = 40100;
    public static final int TOKEN_REVOKED = 40101;
    public static final int LOGIN_BAD_CREDENTIALS = 40110;
    public static final int LOGIN_ACCOUNT_DISABLED = 40111;
    public static final int LOGIN_ACCOUNT_LOCKED = 40112;

    public static final int FORBIDDEN = 40300;
    public static final int SELF_OPERATION_FORBIDDEN = 40310;
    public static final int LAST_ADMIN_ROLE_FORBIDDEN = 40311;
    public static final int LAST_SUPER_ADMIN_FORBIDDEN = 40312;
    public static final int BUILTIN_ROLE_FORBIDDEN = 40320;
    public static final int BUILTIN_ROLE_ALL_PERMISSION_FORBIDDEN = 40321;
    public static final int PERMISSION_CODE_IMMUTABLE = 40330;
    public static final int BUILTIN_PERMISSION_FORBIDDEN = 40331;

    public static final int NOT_FOUND = 40400;

    public static final int UNIQUE_CONFLICT = 40900;
    public static final int ROLE_REFERENCED = 40910;
    public static final int PERMISSION_REFERENCED = 40920;
    public static final int INHERIT_CYCLE = 40930;
    public static final int INHERIT_DEPTH_EXCEEDED = 40931;
    public static final int INHERIT_ROLE_DISABLED = 40932;
    public static final int CONSTRAINT_MUTEX_VIOLATED = 40940;
    public static final int CONSTRAINT_CARDINALITY_VIOLATED = 40941;
    public static final int VERSION_CONFLICT = 40950;

    public static final int RATE_LIMITED = 42900;

    public static final int INTERNAL_ERROR = 50000;
    public static final int SERVICE_DEGRADED = 50300;

    private ErrorCodes() {
    }
}
