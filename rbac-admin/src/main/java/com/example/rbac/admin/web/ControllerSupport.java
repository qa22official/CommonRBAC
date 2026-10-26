package com.example.rbac.admin.web;

import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.RequestContext;
import jakarta.servlet.http.HttpServletRequest;

final class ControllerSupport {

    private ControllerSupport() {
    }

    static CurrentUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(RequestContext.CURRENT_USER_ATTRIBUTE);
        if (value instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new BusinessException(ErrorCodes.TOKEN_INVALID, "未登录或登录状态已失效");
    }

    static String traceId() {
        return RequestContext.traceId();
    }
}
