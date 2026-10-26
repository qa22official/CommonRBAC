package com.example.rbac.guard;

import com.example.rbac.api.annotation.PublicApi;
import com.example.rbac.api.annotation.RequiresLogin;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.permission.PermissionMatcher;
import com.example.rbac.api.permission.PermissionResolver;
import com.example.rbac.api.permission.PermissionStatusQuery;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.RequestContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Set;

/**
 * Steps 2–4 of the authorization chain: forced password change, exactly one
 * method-level declaration, permission resolution and matching. Missing
 * declarations are denied and audited (02 §10).
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionInterceptor.class);

    private final PermissionResolver permissionResolver;
    private final PermissionStatusQuery permissionStatus;
    private final AuditRecorder auditRecorder;
    private final MeterRegistry meterRegistry;

    public PermissionInterceptor(PermissionResolver permissionResolver,
                                 PermissionStatusQuery permissionStatus,
                                 AuditRecorder auditRecorder,
                                 MeterRegistry meterRegistry) {
        this.permissionResolver = permissionResolver;
        this.permissionStatus = permissionStatus;
        this.auditRecorder = auditRecorder;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isFrameworkPath(path)) {
            return true;
        }
        if (hasMethodOrClass(handlerMethod, PublicApi.class)) {
            return true;
        }
        if (Boolean.TRUE.equals(request.getAttribute(RequestContext.MUST_CHANGE_ATTRIBUTE))
                && !isAllowedWhilePasswordChangeRequired(request, path)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "请先修改初始密码");
        }
        Object authError = request.getAttribute(RequestContext.AUTH_ERROR_ATTRIBUTE);
        if (authError instanceof BusinessException businessException) {
            throw businessException;
        }
        CurrentUser currentUser = currentUser(request);
        if (currentUser == null) {
            throw new BusinessException(ErrorCodes.TOKEN_INVALID, "未登录或登录状态已失效");
        }
        if (hasMethodOrClass(handlerMethod, RequiresLogin.class)) {
            return true;
        }
        RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (required != null) {
            return checkPermission(currentUser, required.value(), path, request);
        }
        log.warn("Endpoint without an authorization declaration denied: {} {}", request.getMethod(), path);
        auditDenied(currentUser, null, path, request, "missing authorization declaration");
        countDenied("none");
        throw new BusinessException(ErrorCodes.FORBIDDEN, "无操作权限");
    }

    private boolean checkPermission(CurrentUser currentUser, String requiredCode,
                                    String path, HttpServletRequest request) {
        if (requiredCode == null || requiredCode.isBlank()) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "无操作权限");
        }
        if (!permissionStatus.available()) {
            throw new BusinessException(ErrorCodes.SERVICE_DEGRADED, "鉴权数据暂不可用");
        }
        Set<String> granted = permissionResolver.resolve(currentUser.userId());
        if (!PermissionMatcher.matches(granted, requiredCode)) {
            auditDenied(currentUser, requiredCode, path, request, "permission denied");
            countDenied(requiredCode);
            throw new BusinessException(ErrorCodes.FORBIDDEN, "无操作权限");
        }
        return true;
    }

    private boolean isAllowedWhilePasswordChangeRequired(HttpServletRequest request, String path) {
        String method = request.getMethod();
        return ("PUT".equals(method) && "/api/auth/password".equals(path))
                || ("POST".equals(method) && "/api/auth/logout".equals(path))
                || ("GET".equals(method) && "/api/auth/me".equals(path));
    }

    private boolean isFrameworkPath(String path) {
        return path.equals("/error") || path.startsWith("/actuator");
    }

    private boolean hasMethodOrClass(HandlerMethod handlerMethod, Class<? extends java.lang.annotation.Annotation> type) {
        return handlerMethod.hasMethodAnnotation(type)
                || handlerMethod.getBeanType().isAnnotationPresent(type);
    }

    private CurrentUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(RequestContext.CURRENT_USER_ATTRIBUTE);
        return value instanceof CurrentUser currentUser ? currentUser : null;
    }

    private void auditDenied(CurrentUser currentUser, String requiredCode, String path,
                             HttpServletRequest request, String reason) {
        try {
            String clientIp = RequestContext.clientIp();
            auditRecorder.record(new AuditRecord(
                    AuditCategory.SECURITY,
                    currentUser.userId(),
                    currentUser.username(),
                    "PERM_DENIED",
                    "API",
                    null,
                    null,
                    "{\"path\":\"" + escape(path) + "\",\"method\":\"" + request.getMethod()
                            + "\",\"requiredCode\":\"" + escape(requiredCode) + "\"}",
                    false,
                    reason,
                    clientIp == null ? "internal" : clientIp,
                    RequestContext.userAgent(),
                    RequestContext.traceId(),
                    null,
                    Instant.now(),
                    null));
        } catch (RuntimeException ex) {
            log.warn("Could not record permission-denied audit: {}", ex.getMessage());
        }
    }

    private void countDenied(String permission) {
        Counter.builder("rbac.guard.denied")
                .description("Requests rejected by the permission interceptor")
                .tag("permission", permission == null ? "none" : permission)
                .register(meterRegistry)
                .increment();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
