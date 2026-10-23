package com.example.rbac.auth.internal;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 登录失败计数 + 账号锁定 + 限流（账号 5 次/分钟、IP 20 次/分钟）。
 * Redis 可用时存 Redis；本实现用内存 ConcurrentHashMap，单实例足够；多实例场景换成 Redis 计数。
 */
@Component
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_FAILS = 5;            // 5 次连续失败 → 锁定
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int ACCOUNT_RATE = 5;          // 账号维度：5 次/分钟
    private static final int IP_RATE = 20;              // IP 维度：20 次/分钟
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentMap<String, Long> lockedUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> failCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> accountRate = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> ipRate = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> accountRateReset = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> ipRateReset = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        Long t = lockedUntil.get(username);
        return t != null && t > System.currentTimeMillis();
    }

    public String recordFailure(String username, String ip) {
        // 限流前置
        if (!checkRate("acc:" + username, ACCOUNT_RATE, accountRate, accountRateReset)) return "登录尝试过于频繁";
        if (!checkRate("ip:" + ip, IP_RATE, ipRate, ipRateReset)) return "IP 登录尝试过于频繁";

        int count = failCount.merge(username, 1, Integer::sum);
        if (count >= MAX_FAILS) {
            lockedUntil.put(username, System.currentTimeMillis() + LOCK_DURATION.toMillis());
            log.info("account locked user={} for {}s", username, LOCK_DURATION.toSeconds());
            return "账号已锁定 15 分钟";
        }
        return null;
    }

    public void recordSuccess(String username) {
        failCount.remove(username);
        lockedUntil.remove(username);
    }

    private boolean checkRate(String key, int limit,
                              ConcurrentMap<String, AtomicInteger> counters,
                              ConcurrentMap<String, Long> resets) {
        long now = System.currentTimeMillis();
        long resetAt = resets.getOrDefault(key, now + WINDOW.toMillis());
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        if (now >= resetAt) {
            counter.set(1);
            resets.put(key, now + WINDOW.toMillis());
            return true;
        }
        return counter.incrementAndGet() <= limit;
    }
}
