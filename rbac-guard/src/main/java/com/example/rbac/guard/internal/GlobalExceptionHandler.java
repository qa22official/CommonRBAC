package com.example.rbac.guard.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.ConflictException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.auth.internal.AuthException;
import com.example.rbac.core.internal.validation.ConstraintViolationException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 全局异常处理器。确保所有异常都转为 ApiResponse。严禁把内部细节透传到响应体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ApiResponse<Void> auth(AuthException e) {
        return ApiResponse.fail(e.code, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ApiResponse<Void> conflict(ConflictException e) {
        return ApiResponse.fail(ErrorCodes.VERSION_CONFLICT, "version conflict, please retry");
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ApiResponse<Void> dup(org.springframework.dao.DuplicateKeyException e) {
        return ApiResponse.fail(409, "data already exists");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> constraint(ConstraintViolationException e) {
        return ApiResponse.fail(ErrorCodes.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> badBody(HttpMessageNotReadableException e) {
        return ApiResponse.fail(400, "bad request body");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> all(Exception e) {
        log.error("unhandled", e);
        return ApiResponse.fail(500, "internal error");
    }
}
