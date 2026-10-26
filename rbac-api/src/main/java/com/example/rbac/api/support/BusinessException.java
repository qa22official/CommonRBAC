package com.example.rbac.api.support;

/**
 * Business failure carrying a canonical error code. The HTTP status is derived
 * from the code prefix so every module reports failures consistently.
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static BusinessException of(int code, String message) {
        return new BusinessException(code, message);
    }
}
