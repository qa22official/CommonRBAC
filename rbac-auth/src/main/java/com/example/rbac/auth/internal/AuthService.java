package com.example.rbac.auth.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.cache.internal.CachePublisher;
import com.example.rbac.core.internal.domain.User;
import com.example.rbac.core.internal.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

/**
 * 登录 / 登出 / 刷新 / 改密 四个核心流程。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserMapper userMapper;
    private final JwtService jwt;
    private final LoginAttemptService attempts;
    private final AuditRecorder audit;
    private final CachePublisher cachePublisher;

    public AuthService(UserMapper userMapper, JwtService jwt,
                       LoginAttemptService attempts, AuditRecorder audit,
                       CachePublisher cachePublisher) {
        this.userMapper = userMapper;
        this.jwt = jwt;
        this.attempts = attempts;
        this.audit = audit;
        this.cachePublisher = cachePublisher;
    }

    /** 登录。返回 token 或抛业务异常（统一异常转 401）。 */
    public LoginResult login(String username, String password, String ip, String userAgent, String traceId) {
        if (attempts.isLocked(username)) {
            audit.record(new AuditRecord(AuditCategory.SECURITY, "LOGIN_REJECT", null, null,
                    null, "USER", username, null, null, AuditRecord.RESULT_FAIL, "ACCOUNT_LOCKED",
                    ip, userAgent, traceId, null));
            throw new AuthException(ErrorCodes.UNAUTHENTICATED, "账号已锁定");
        }

        User user = userMapper.findByUsername(username).orElse(null);
        if (user == null || user.status() != 1) {
            String msg = attempts.recordFailure(username, ip);
            audit.record(new AuditRecord(AuditCategory.SECURITY, "LOGIN_FAIL", null, null,
                    null, "USER", username, null, null, AuditRecord.RESULT_FAIL, msg == null ? "BAD_CREDENTIALS" : msg,
                    ip, userAgent, traceId, null));
            throw new AuthException(ErrorCodes.UNAUTHENTICATED, "账号或密码错误");
        }

        if (!PasswordPolicy.matches(password, user.passwordHash())) {
            String msg = attempts.recordFailure(username, ip);
            audit.record(new AuditRecord(AuditCategory.SECURITY, "LOGIN_FAIL", user.nickname(), user.id(),
                    null, "USER", username, null, null, AuditRecord.RESULT_FAIL, msg == null ? "BAD_CREDENTIALS" : msg,
                    ip, userAgent, traceId, null));
            throw new AuthException(ErrorCodes.UNAUTHENTICATED, "账号或密码错误");
        }

        // 成功 → 自增 token_version 作废历史令牌
        userMapper.bumpTokenVersion(user.id());
        User fresh = userMapper.findById(user.id()).orElse(user);
        attempts.recordSuccess(username);

        broadcastTokenRevoked(user.id());

        String token = jwt.issue(user.id(), fresh.tokenVersion());
        audit.record(new AuditRecord(AuditCategory.SECURITY, "LOGIN", fresh.nickname(), fresh.id(),
                transactionId(), "USER", username, null, null, AuditRecord.RESULT_SUCCESS, null,
                ip, userAgent, traceId, null));

        return new LoginResult(token, fresh.tokenVersion(), fresh.id(), fresh.nickname());
    }

    public void logout(long userId, String ip, String userAgent, String traceId) {
        userMapper.bumpTokenVersion(userId);
        broadcastTokenRevoked(userId);
        audit.record(new AuditRecord(AuditCategory.SECURITY, "LOGOUT", null, userId,
                transactionId(), "USER", String.valueOf(userId), null, null, AuditRecord.RESULT_SUCCESS, null,
                ip, userAgent, traceId, null));
    }

    public RefreshResult refresh(long userId, String ip, String userAgent, String traceId) {
        User user = userMapper.findById(userId).orElse(null);
        if (user == null || user.status() != 1) {
            throw new AuthException(ErrorCodes.TOKEN_REVOKED, "用户已停用或不存在");
        }
        String newToken = jwt.issue(user.id(), user.tokenVersion());
        return new RefreshResult(newToken, user.tokenVersion());
    }

    @Transactional
    public void changePassword(long userId, String oldPwd, String newPwd, String ip, String userAgent, String traceId) {
        User user = userMapper.findById(userId).orElseThrow(() -> new AuthException(ErrorCodes.UNAUTHENTICATED, "用户不存在"));
        if (!PasswordPolicy.matches(oldPwd, user.passwordHash())) {
            throw new AuthException(ErrorCodes.UNAUTHENTICATED, "原密码错误");
        }
        String reason = PasswordPolicy.validate(user.username(), newPwd);
        if (reason != null) throw new AuthException(ErrorCodes.UNAUTHENTICATED, reason);

        // 新密 + 自增 token_version
        userMapper.updatePasswordAndBump(user.id(), PasswordPolicy.hash(newPwd));
        broadcastTokenRevoked(userId);

        audit.record(new AuditRecord(AuditCategory.CHANGE, "CHANGE_PASSWORD", user.nickname(), userId,
                transactionId(), "USER", String.valueOf(userId), "{\"password\": \"***\"}",
                "{\"password\": \"***\"}", AuditRecord.RESULT_SUCCESS, null,
                ip, userAgent, traceId, null));
    }

    /** 本地缓存立即失效 + Redis 广播跨实例失效（统一走 CachePublisher）。 */
    private void broadcastTokenRevoked(long userId) {
        cachePublisher.publishTokenRevoked(userId);
    }

    private String transactionId() { return java.util.UUID.randomUUID().toString(); }

    public record LoginResult(String token, long tokenVersion, long userId, String nickname) {}
    public record RefreshResult(String token, long tokenVersion) {}
}
