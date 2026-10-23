package com.example.rbac.auth.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.WeakKeyException;

/**
 * JWT 签发与验签：HS256 + 多 kid 轮换 + 允许 60 秒时钟偏移。
 * 载荷：sub(userId) / ver(tokenVersion) / kid / iss / aud / iat / exp。
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String SUB  = "sub";
    private static final String VER  = "ver";
    private static final String KID  = "kid";
    private static final String ISS  = "iss";
    private static final String AUD  = "aud";

    private final Map<String, SecretKey> keys = new LinkedHashMap<>();
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;
    private final long clockSkewSeconds;

    public JwtService(@Value("${rbac.auth.jwt-keys}") String keysConfig,
                      @Value("${rbac.auth.issuer}") String issuer,
                      @Value("${rbac.auth.audience}") String audience,
                      @Value("${rbac.auth.access-token-seconds:7200}") long ttlSeconds,
                      @Value("${rbac.auth.clock-skew-seconds:60}") long clockSkewSeconds) {
        this.issuer = issuer;
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
        this.clockSkewSeconds = clockSkewSeconds;
        log.info("JwtService init: issuer=[{}] audience=[{}] ttl={} skew={}", issuer, audience, ttlSeconds, clockSkewSeconds);

        // "kid1:base64key,kid2:base64key2"
        for (String entry : keysConfig.split(",")) {
            int idx = entry.indexOf(':');
            if (idx < 0) continue;
            String kid = entry.substring(0, idx).trim();
            String b64 = entry.substring(idx + 1).trim();
            try {
                SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(b64), "HmacSHA256");
                if (key.getEncoded().length < 32) {
                    throw new WeakKeyException("HS256 requires ≥ 256-bit key, kid=" + kid);
                }
                keys.put(kid, key);
            } catch (Exception e) {
                throw new IllegalStateException("bad jwt key for kid=" + kid + ": " + e.getMessage(), e);
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("no jwt keys configured");
        }
    }

    public String kid() {
        return keys.keySet().iterator().next(); // 第一个是签发方
    }

    /** 签发令牌。 */
    public String issue(long userId, long tokenVersion) {
        String kid = kid();
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .header()
                    .keyId(kid)
                    .and()
                .subject(String.valueOf(userId))
                .claim(VER, tokenVersion)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlSeconds * 1000))
                .signWith(keys.get(kid))
                .compact();
    }

    /** 验签。返回 Claims；失败返回 null。 */
    public Claims parse(String token) {
        try {
            // 先解 header 拿 kid
            String[] parts = token.split("\\.");
            if (parts.length != 3) { log.warn("jwt: wrong part count: {}", parts.length); return null; }
            String headerRaw = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String kid = extractKid(headerRaw);
            SecretKey key = kid == null ? null : keys.get(kid);
            if (key == null) { log.warn("jwt: kid unknown, header={}", headerRaw); return null; }

            long skewMs = clockSkewSeconds * 1000L;
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .clock(new io.jsonwebtoken.Clock() {
                        @Override public java.util.Date now() {
                            return new java.util.Date(System.currentTimeMillis() - skewMs);
                        }
                    })
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("jwt parse fail: {}", e.getMessage());
            return null;
        }
    }

    private String extractKid(String headerJson) {
        // 极简 JSON 解析，避免引入 Jackson 依赖
        int kidIdx = headerJson.indexOf("\"kid\"");
        if (kidIdx < 0) return null;
        int start = headerJson.indexOf('"', kidIdx + 6) + 1;
        int end   = headerJson.indexOf('"', start);
        return headerJson.substring(start, end);
    }
}
