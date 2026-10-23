package com.example.rbac.auth.internal;

import com.example.rbac.api.support.ErrorCodes;

/** 鉴权层业务异常——由全局异常处理器转换为正确的 HTTP 状态码。 */
public class AuthException extends RuntimeException {
    public final int code;
    public AuthException(int code, String msg) { super(msg); this.code = code; }
}
