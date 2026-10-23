package com.example.rbac.guard.internal;

import java.lang.reflect.Method;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.rbac.api.annotation.NoAudit;
import com.example.rbac.api.annotation.PublicApi;
import com.example.rbac.api.annotation.RequiresLogin;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.permission.FrameworkPermissionCodes;
import com.example.rbac.api.permission.PermissionResolver;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.RequestContext;
import com.example.rbac.core.internal.permission.WildcardMatcher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 固定顺序第 2 步：注解判定 + 权限码匹配。未标注解 → 403 + 告警（默认拒绝）。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionResolver resolver;
    private final ErrorWriter errWriter;

    public PermissionInterceptor(PermissionResolver resolver, ErrorWriter errWriter) {
        this.resolver = resolver;
        this.errWriter = errWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) return true;
        Method m = hm.getMethod();

        PublicApi publicApi = m.getAnnotation(PublicApi.class);
        if (publicApi != null) return true; // 含未登录

        RequiresLogin login = m.getAnnotation(RequiresLogin.class);
        RequiresPermission perm = m.getAnnotation(RequiresPermission.class);

        if (login == null && perm == null) {
            // 默认拒绝
            errWriter.write(response, ErrorCodes.FORBIDDEN, "missing auth annotation on " + hm.getBeanType().getName() + "#" + m.getName());
            return false;
        }

        CurrentUser cur = RequestContext.getCurrentUser();
        if (cur == null) {
            errWriter.write(response, ErrorCodes.UNAUTHENTICATED, "login required");
            return false;
        }

        if (login != null && perm == null) return true;

        // 需要权限码
        Set<String> codes = resolver.resolve(cur.userId());
        String required = perm.value();
        // 主链路 O(1)：集合 contains + 通配前缀匹配
        for (String owned : codes) {
            if (WildcardMatcher.matches(owned, required)) return true;
        }

        errWriter.write(response, ErrorCodes.FORBIDDEN, "permission denied: " + required);
        return false;
    }
}
