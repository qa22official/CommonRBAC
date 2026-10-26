package com.example.rbac.auth.internal;

import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Redis sliding-window limits: 5 attempts/minute per account and 20/minute per
 * client IP. When Redis is unavailable (or disabled) the limiter fails open;
 * the database-backed account lock remains enforced (01 FR-A8).
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final int ACCOUNT_LIMIT = 5;
    private static final int IP_LIMIT = 20;
    private static final long WINDOW_MILLIS = 60_000L;
    private static final String SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            if redis.call('ZCARD', key) >= limit then
                return 0
            end
            redis.call('ZADD', key, now, ARGV[4])
            redis.call('PEXPIRE', key, window)
            return 1
            """;
    private static final DefaultRedisScript<Long> SLIDING_WINDOW = new DefaultRedisScript<>(SCRIPT, Long.class);

    private final StringRedisTemplate redis;
    private final boolean redisEnabled;
    private volatile long lastWarningAt;

    public LoginRateLimiter(StringRedisTemplate redis,
                            @Value("${rbac.redis.enabled:true}") boolean redisEnabled) {
        this.redis = redis;
        this.redisEnabled = redisEnabled;
    }

    public void check(String username, String clientIp) {
        if (!redisEnabled) {
            return;
        }
        try {
            if (!allow("rbac:login:account:" + username, ACCOUNT_LIMIT)) {
                throw new BusinessException(ErrorCodes.RATE_LIMITED, "操作过于频繁，请稍后重试");
            }
            if (clientIp != null && !allow("rbac:login:ip:" + clientIp, IP_LIMIT)) {
                throw new BusinessException(ErrorCodes.RATE_LIMITED, "操作过于频繁，请稍后重试");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            long now = System.currentTimeMillis();
            if (now - lastWarningAt > 60_000L) {
                lastWarningAt = now;
                log.warn("Redis unavailable; login rate limiting is disabled: {}", ex.getMessage());
            }
        }
    }

    private boolean allow(String key, int limit) {
        Long result = redis.execute(SLIDING_WINDOW, List.of(key),
                Long.toString(System.currentTimeMillis()),
                Long.toString(WINDOW_MILLIS),
                Integer.toString(limit),
                UUID.randomUUID().toString());
        return result == null || result == 1L;
    }
}
