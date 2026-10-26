package com.example.rbac.config;

import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.RequestContext;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every failure to the unified body and derives HTTP status from the code
 * prefix (04 §1.3). Stack traces, SQL and permission structures never leak.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        int code = ex.code();
        HttpStatus status = statusFor(code);
        if (status.is5xxServerError()) {
            log.error("business failure code={} traceId={}: {}", code, traceId(), ex.getMessage());
        } else {
            log.debug("business failure code={}: {}", code, ex.getMessage());
        }
        HttpHeaders headers = new HttpHeaders();
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            headers.set(HttpHeaders.RETRY_AFTER, "60");
        } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            headers.set(HttpHeaders.RETRY_AFTER, "30");
        }
        return new ResponseEntity<>(ApiResponse.failure(code, ex.getMessage(), traceId()), headers, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError error = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = error == null ? "参数校验失败" : error.getField() + ": " + error.getDefaultMessage();
        return badRequest(message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        return badRequest("参数校验失败");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream().findFirst()
                .map(violation -> violation.getMessage()).orElse("参数校验失败");
        return badRequest(message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformed(Exception ex) {
        return badRequest("请求参数格式不正确");
    }

    /**
     * The pre-insert duplicate checks cover the common path, but two requests
     * can still race to the database. The unique constraint is the real
     * guarantee and must surface as 40900, never as 50000 (04 §1.7).
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        log.debug("unique constraint conflict: {}", ex.getMessage());
        return new ResponseEntity<>(
                ApiResponse.failure(ErrorCodes.UNIQUE_CONFLICT, "数据已存在，请更换后重试", traceId()),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return new ResponseEntity<>(ApiResponse.failure(ErrorCodes.NOT_FOUND, "资源不存在", traceId()),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // 06 R-11 / 00 §5: logs and responses must not contain stack traces.
        // Keep the traceId and exception type for correlation without dumping
        // the stack or SQL into the log.
        log.error("unhandled server error traceId={} type={}",
                traceId(), ex.getClass().getName());
        return new ResponseEntity<>(
                ApiResponse.failure(ErrorCodes.INTERNAL_ERROR, "系统繁忙，请稍后重试", traceId()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return new ResponseEntity<>(ApiResponse.failure(ErrorCodes.PARAM_INVALID, message, traceId()),
                HttpStatus.BAD_REQUEST);
    }

    static HttpStatus statusFor(int code) {
        int prefix = code / 100;
        if (prefix >= 400 && prefix <= 599) {
            HttpStatus status = HttpStatus.resolve(prefix);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String traceId() {
        return RequestContext.traceId();
    }
}
