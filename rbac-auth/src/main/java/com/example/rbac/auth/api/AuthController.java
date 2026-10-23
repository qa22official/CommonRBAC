package com.example.rbac.auth.api;

import org.springframework.web.bind.annotation.*;

import com.example.rbac.api.annotation.NoAudit;
import com.example.rbac.api.annotation.PublicApi;
import com.example.rbac.api.annotation.RequiresLogin;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.ClientIpResolver;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.RequestContext;
import com.example.rbac.auth.internal.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final ClientIpResolver resolver;

    public AuthController(AuthService service, ClientIpResolver resolver) {
        this.service = service;
        this.resolver = resolver;
    }

    @PublicApi
    @NoAudit("SECURITY 审计在 AuthService 内直接调用")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest r) {
        AuthService.LoginResult rlt = service.login(req.username(), req.password(),
                resolver.resolve(r.getRemoteAddr(), r.getHeader("X-Forwarded-For")),
                r.getHeader("User-Agent"), RequestContext.getTraceId());
        return ApiResponse.ok(new LoginResponse(rlt.token(), 7200, rlt.tokenVersion(), rlt.userId(), rlt.nickname()), RequestContext.getTraceId());
    }

    @RequiresLogin
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest r) {
        service.logout(RequestContext.getCurrentUser().userId(),
                resolver.resolve(r.getRemoteAddr(), r.getHeader("X-Forwarded-For")),
                r.getHeader("User-Agent"), RequestContext.getTraceId());
        return ApiResponse.ok(null, RequestContext.getTraceId());
    }

    @RequiresLogin
    @NoAudit("SECURITY 审计在 AuthService 内直接调用")
    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(HttpServletRequest r) {
        AuthService.RefreshResult rr = service.refresh(RequestContext.getCurrentUser().userId(),
                resolver.resolve(r.getRemoteAddr(), r.getHeader("X-Forwarded-For")),
                r.getHeader("User-Agent"), RequestContext.getTraceId());
        return ApiResponse.ok(new RefreshResponse(rr.token(), 7200, rr.tokenVersion()), RequestContext.getTraceId());
    }

    @RequiresLogin
    @NoAudit("CHANGE 审计在 AuthService 内直接调用")
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest req, HttpServletRequest r) {
        service.changePassword(RequestContext.getCurrentUser().userId(),
                req.oldPassword(), req.newPassword(),
                resolver.resolve(r.getRemoteAddr(), r.getHeader("X-Forwarded-For")),
                r.getHeader("User-Agent"), RequestContext.getTraceId());
        return ApiResponse.ok(null, RequestContext.getTraceId());
    }
}
