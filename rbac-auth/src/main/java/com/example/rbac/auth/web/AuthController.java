package com.example.rbac.auth.web;

import com.example.rbac.api.annotation.NoAudit;
import com.example.rbac.api.annotation.PublicApi;
import com.example.rbac.api.annotation.RequiresLogin;
import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.RequestContext;
import com.example.rbac.auth.internal.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @PublicApi
    public ApiResponse<AuthService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request.username(), request.password()),
                RequestContext.traceId());
    }

    @GetMapping("/me")
    @RequiresLogin
    @NoAudit("纯读，不改变数据")
    public ApiResponse<AuthService.MeResult> me(HttpServletRequest request) {
        CurrentUser current = currentUser(request);
        return ApiResponse.success(authService.me(current.userId()), RequestContext.traceId());
    }

    @PostMapping("/logout")
    @RequiresLogin
    public ApiResponse<Void> logout(HttpServletRequest request) {
        CurrentUser current = currentUser(request);
        authService.logout(current.userId(), current.username());
        return ApiResponse.success(null, RequestContext.traceId());
    }

    @PostMapping("/refresh")
    @RequiresLogin
    @NoAudit("仅重新签发令牌，不改变权限事实")
    public ApiResponse<AuthService.TokenResult> refresh(HttpServletRequest request) {
        CurrentUser current = currentUser(request);
        return ApiResponse.success(
                authService.refresh(current.userId(), current.tokenVersion(), current.username()),
                RequestContext.traceId());
    }

    @PutMapping("/password")
    @RequiresLogin
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                            HttpServletRequest request) {
        CurrentUser current = currentUser(request);
        authService.changePassword(current.userId(), current.username(),
                body.oldPassword(), body.newPassword());
        return ApiResponse.success(null, RequestContext.traceId());
    }

    @PostMapping("/permit")
    @RequiresLogin
    @NoAudit("纯鉴权判定")
    public ApiResponse<AuthService.PermitResult> permit(@Valid @RequestBody PermitRequest body,
                                                        HttpServletRequest request,
                                                        HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        CurrentUser current = currentUser(request);
        return ApiResponse.success(authService.permit(current.userId(), body.permissionCode()),
                RequestContext.traceId());
    }

    @PostMapping("/permission-check")
    @RequiresLogin
    @NoAudit("纯鉴权判定")
    public ApiResponse<AuthService.PermissionCheckResult> permissionCheck(
            @Valid @RequestBody PermissionCheckRequest body,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(
                authService.permissionCheck(body.userId(), body.permissionCodes()),
                RequestContext.traceId());
    }

    private CurrentUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(RequestContext.CURRENT_USER_ATTRIBUTE);
        if (value instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new BusinessException(ErrorCodes.TOKEN_INVALID, "未登录或登录状态已失效");
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 72) String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String oldPassword,
            @NotBlank @Size(max = 72) String newPassword) {
    }

    public record PermitRequest(@NotBlank String permissionCode) {
    }

    public record PermissionCheckRequest(
            @Positive long userId,
            @NotEmpty @Size(max = 50) List<@NotBlank String> permissionCodes) {
    }
}
