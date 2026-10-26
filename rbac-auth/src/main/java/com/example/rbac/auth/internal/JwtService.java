package com.example.rbac.auth.internal;

import com.example.rbac.api.auth.TokenVerifier;
import com.example.rbac.api.auth.VerifiedToken;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HS256 JWT signing and verification with a kid-keyed key ring. Validation
 * always enforces signature, issuer, audience, expiry and issued-at (with a
 * configurable clock skew). Unknown kids are treated as 40100 (ADR-23).
 */
@Service
public class JwtService implements TokenVerifier {

    private final JwtProperties properties;
    private final Map<String, SecretKey> keys = new LinkedHashMap<>();

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        if (properties.getKeys() == null || properties.getKeys().isEmpty()) {
            throw new IllegalStateException("rbac.jwt.keys must contain at least one key");
        }
        properties.getKeys().forEach((kid, secret) -> {
            if (kid == null || kid.isBlank()) {
                throw new IllegalStateException("JWT key id must not be blank");
            }
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("JWT secret for kid " + kid + " must be at least 256 bits");
            }
            keys.put(kid, Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)));
        });
        String active = properties.getActiveKid();
        if (active == null || active.isBlank()) {
            active = keys.keySet().iterator().next();
            properties.setActiveKid(active);
        }
        if (!keys.containsKey(active)) {
            throw new IllegalStateException("rbac.jwt.active-kid does not exist in the key ring");
        }
    }

    public String sign(long userId, long tokenVersion, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(properties.getActiveKid()).and()
                .subject(Long.toString(userId))
                .claim("ver", tokenVersion)
                .claim("username", username)
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getTtl())))
                .signWith(keys.get(properties.getActiveKid()), Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public VerifiedToken verify(String token) {
        try {
            var parsed = Jwts.parser()
                    .keyLocator(new KidLocator())
                    .requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience())
                    .clockSkewSeconds(properties.getClockSkewSeconds())
                    .build()
                    .parseSignedClaims(token);
            Claims claims = parsed.getPayload();
            long userId = Long.parseLong(claims.getSubject());
            Number version = claims.get("ver", Number.class);
            String username = claims.get("username", String.class);
            if (version == null || username == null || username.isBlank()) {
                throw invalid();
            }
            if (claims.getIssuedAt() != null
                    && claims.getIssuedAt().toInstant().isAfter(Instant.now().plusSeconds(properties.getClockSkewSeconds()))) {
                throw invalid();
            }
            return new VerifiedToken(userId, version.longValue(), username, parsed.getHeader().getKeyId());
        } catch (BusinessException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCodes.TOKEN_INVALID, "登录令牌无效或已过期");
    }

    private final class KidLocator implements Locator<Key> {

        @Override
        public Key locate(io.jsonwebtoken.Header header) {
            Object kid = header.get("kid");
            if (kid == null) {
                throw invalid();
            }
            Key key = keys.get(kid.toString());
            if (key == null) {
                throw invalid();
            }
            return key;
        }
    }
}
