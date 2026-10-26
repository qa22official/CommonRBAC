package com.example.rbac.auth.internal;

import com.example.rbac.api.auth.AuthAccount;
import com.example.rbac.api.dto.RoleSummary;
import com.example.rbac.api.dto.user.UserProfile;
import com.example.rbac.api.identity.IdentityQuery;
import com.example.rbac.api.identity.IdentitySnapshot;
import com.example.rbac.api.permission.PermissionCodeFormat;
import com.example.rbac.api.permission.PermissionMatcher;
import com.example.rbac.api.permission.PermissionResolver;
import com.example.rbac.api.permission.PermissionStatusQuery;
import com.example.rbac.api.security.PasswordHasher;
import com.example.rbac.api.security.PasswordPolicy;
import com.example.rbac.api.service.UserAdminService;
import com.example.rbac.api.service.UserAuthService;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.auth.config.JwtProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService {

    private static final int STATUS_ENABLED = 1;

    private final UserAuthService userAuthService;
    private final UserAdminService userAdminService;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter rateLimiter;
    private final PermissionResolver permissionResolver;
    private final PermissionStatusQuery permissionStatus;
    private final IdentityQuery identityQuery;
    private final SecurityAuditor securityAuditor;
    private final AuthEvents authEvents;
    private final String dummyHash;

    public AuthService(UserAuthService userAuthService,
                       UserAdminService userAdminService,
                       PasswordHasher passwordHasher,
                       PasswordPolicy passwordPolicy,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       LoginRateLimiter rateLimiter,
                       PermissionResolver permissionResolver,
                       PermissionStatusQuery permissionStatus,
                       IdentityQuery identityQuery,
                       SecurityAuditor securityAuditor,
                       AuthEvents authEvents) {
        this.userAuthService = userAuthService;
        this.userAdminService = userAdminService;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.rateLimiter = rateLimiter;
        this.permissionResolver = permissionResolver;
        this.permissionStatus = permissionStatus;
        this.identityQuery = identityQuery;
        this.securityAuditor = securityAuditor;
        this.authEvents = authEvents;
        this.dummyHash = passwordHasher.hash("dummy-password-not-used");
    }

    public LoginResult login(String username, String password) {
        String masked = SecurityAuditor.mask(username);
        try {
            rateLimiter.check(username, com.example.rbac.api.support.RequestContext.clientIp());
        } catch (BusinessException ex) {
            securityAuditor.record(null, masked, "LOGIN_RATE_LIMITED", "SESSION", null,
                    false, "rate limited", null);
            throw ex;
        }
        Optional<AuthAccount> optional = userAuthService.findForLogin(username);
        if (optional.isEmpty()) {
            // Equalize response time so account existence cannot be probed.
            passwordHasher.matches(password, dummyHash);
            securityAuditor.record(null, masked, "LOGIN", "SESSION", null,
                    false, "账号或密码错误", null);
            throw new BusinessException(ErrorCodes.LOGIN_BAD_CREDENTIALS, "账号或密码错误");
        }
        AuthAccount account = optional.get();
        if (account.status() != STATUS_ENABLED) {
            securityAuditor.record(account.id(), account.username(), "LOGIN", "SESSION", account.id(),
                    false, "账号已停用", null);
            throw new BusinessException(ErrorCodes.LOGIN_ACCOUNT_DISABLED, "账号已停用，请联系管理员");
        }
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(Instant.now())) {
            throw locked(account);
        }
        if (account.lockedUntil() != null) {
            userAuthService.resetLoginFailures(account.id());
        }
        if (!passwordHasher.matches(password, account.passwordHash())) {
            userAuthService.recordLoginFailure(account.id());
            AuthAccount refreshed = userAuthService.findForLogin(username).orElse(account);
            if (refreshed.lockedUntil() != null && refreshed.lockedUntil().isAfter(Instant.now())) {
                throw locked(refreshed);
            }
            securityAuditor.record(account.id(), account.username(), "LOGIN", "SESSION", account.id(),
                    false, "账号或密码错误", null);
            throw new BusinessException(ErrorCodes.LOGIN_BAD_CREDENTIALS, "账号或密码错误");
        }
        userAuthService.recordLoginSuccess(account.id());
        String token = jwtService.sign(account.id(), account.tokenVersion(), account.username());
        securityAuditor.record(account.id(), account.username(), "LOGIN", "SESSION", account.id(),
                true, null, "{\"mustChangePassword\":" + account.mustChangePassword() + "}");
        return new LoginResult(token, jwtProperties.ttlSeconds(),
                new LoginUser(account.id(), account.username(), account.nickname()),
                account.mustChangePassword());
    }

    public TokenResult refresh(long userId, long tokenVersion, String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ErrorCodes.TOKEN_REVOKED, "登录状态已失效，请重新登录");
        }
        AuthAccount account = userAuthService.findForLogin(username)
                .orElseThrow(() -> new BusinessException(ErrorCodes.TOKEN_REVOKED, "登录状态已失效，请重新登录"));
        if (account.id() != userId || account.status() != STATUS_ENABLED
                || account.tokenVersion() != tokenVersion) {
            throw new BusinessException(ErrorCodes.TOKEN_REVOKED, "登录状态已失效，请重新登录");
        }
        return new TokenResult(jwtService.sign(userId, tokenVersion, account.username()),
                jwtProperties.ttlSeconds());
    }

    public void logout(long userId, String username) {
        long newVersion = userAuthService.incrementTokenVersion(userId);
        securityAuditor.record(userId, username, "LOGOUT", "SESSION", userId, true, null,
                "{\"tokenVersion\":" + newVersion + "}");
        authEvents.tokenRevoked(userId, newVersion);
    }

    public void changePassword(long userId, String username, String oldPassword, String newPassword) {
        AuthAccount account = userAuthService.findForLogin(username)
                .orElseThrow(() -> new BusinessException(ErrorCodes.TOKEN_REVOKED, "登录状态已失效，请重新登录"));
        if (!passwordHasher.matches(oldPassword, account.passwordHash())) {
            securityAuditor.record(userId, username, "PASSWORD_CHANGE", "USER", userId,
                    false, "旧密码错误", null);
            throw new BusinessException(ErrorCodes.LOGIN_BAD_CREDENTIALS, "旧密码错误");
        }
        Optional<String> violation = passwordPolicy.violation(account.username(), newPassword);
        if (violation.isPresent()) {
            securityAuditor.record(userId, username, "PASSWORD_CHANGE", "USER", userId,
                    false, violation.get(), null);
            throw new BusinessException(ErrorCodes.PASSWORD_WEAK, violation.get());
        }
        long newVersion = userAuthService.changePassword(userId, passwordHasher.hash(newPassword));
        securityAuditor.record(userId, username, "PASSWORD_CHANGE", "USER", userId, true, null,
                "{\"tokenVersion\":" + newVersion + "}");
        authEvents.tokenRevoked(userId, newVersion);
    }

    public MeResult me(long userId) {
        UserProfile profile = userAdminService.findProfile(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.TOKEN_REVOKED, "登录状态已失效，请重新登录"));
        // /me also exposes the permission set, so it must respect the same
        // fail-closed posture as the guard instead of returning stale codes.
        if (!permissionStatus.available()) {
            throw new BusinessException(ErrorCodes.SERVICE_DEGRADED, "鉴权数据暂不可用");
        }
        Set<String> permissions = permissionResolver.resolve(userId);
        boolean mustChange = identityQuery.findByUserId(userId)
                .map(IdentitySnapshot::mustChangePassword)
                .orElse(false);
        return new MeResult(profile, permissions, profile.roles(), mustChange);
    }

    public PermitResult permit(long userId, String permissionCode) {
        requireCode(permissionCode);
        if (!permissionStatus.available()) {
            throw new BusinessException(ErrorCodes.SERVICE_DEGRADED, "鉴权数据暂不可用");
        }
        boolean allowed = PermissionMatcher.matches(permissionResolver.resolve(userId), permissionCode);
        return new PermitResult(allowed, userId, permissionStatus.degraded());
    }

    public PermissionCheckResult permissionCheck(long userId, List<String> codes) {
        if (codes == null || codes.isEmpty() || codes.size() > 50) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "permissionCodes 数量必须为 1 到 50");
        }
        for (String code : codes) {
            requireCode(code);
        }
        if (!permissionStatus.available()) {
            throw new BusinessException(ErrorCodes.SERVICE_DEGRADED, "鉴权数据暂不可用");
        }
        boolean degraded = permissionStatus.degraded();
        Optional<IdentitySnapshot> identity = identityQuery.findByUserId(userId);
        if (identity.isEmpty()) {
            return new PermissionCheckResult(userId, degraded, denyAll(codes, "USER_NOT_FOUND"));
        }
        if (!identity.get().enabled()) {
            return new PermissionCheckResult(userId, degraded, denyAll(codes, "USER_DISABLED"));
        }
        Set<String> granted = permissionResolver.resolve(userId);
        List<PermissionCheckItem> results = codes.stream()
                .map(code -> new PermissionCheckItem(code,
                        PermissionMatcher.matches(granted, code),
                        PermissionMatcher.matches(granted, code) ? "ALLOWED" : "NO_PERMISSION"))
                .toList();
        return new PermissionCheckResult(userId, degraded, results);
    }

    private List<PermissionCheckItem> denyAll(List<String> codes, String reason) {
        return codes.stream().map(code -> new PermissionCheckItem(code, false, reason)).toList();
    }

    private void requireCode(String code) {
        if (!PermissionCodeFormat.isValid(code)) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "权限码格式非法：" + code);
        }
    }

    private BusinessException locked(AuthAccount account) {
        long minutes = Math.max(1, Duration.between(Instant.now(), account.lockedUntil()).toMinutes() + 1);
        securityAuditor.record(account.id(), account.username(), "LOGIN", "SESSION", account.id(),
                false, "账号已锁定", null);
        return new BusinessException(ErrorCodes.LOGIN_ACCOUNT_LOCKED,
                "账号已锁定，请 " + minutes + " 分钟后重试");
    }

    public record LoginResult(String accessToken,
                              long expiresIn,
                              LoginUser user,
                              boolean mustChangePassword) {
    }

    public record LoginUser(long id, String username, String nickname) {
    }

    public record TokenResult(String accessToken, long expiresIn) {
    }

    public record MeResult(UserProfile user,
                           Set<String> permissions,
                           List<RoleSummary> roles,
                           boolean mustChangePassword) {
    }

    public record PermitResult(boolean allowed, long userId, boolean degraded) {
    }

    public record PermissionCheckItem(String permissionCode, boolean allowed, String reason) {
    }

    public record PermissionCheckResult(long userId, boolean degraded, List<PermissionCheckItem> results) {
    }
}
