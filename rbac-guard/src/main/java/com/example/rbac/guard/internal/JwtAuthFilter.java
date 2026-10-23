package com.example.rbac.guard.internal;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.RequestContext;
import com.example.rbac.cache.internal.PermissionCache;
import com.example.rbac.auth.internal.JwtService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 固定顺序第 1 步：JWT 验签 → 身份快照校验 → 写入 RequestContext。
 * 不携带 Authorization 头时放行（@PublicApi 接口会在拦截器通过；其他接口拦截器给 403）。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwt;
    private final PermissionCache cache;
    private final ErrorWriter errWriter;

    public JwtAuthFilter(JwtService jwt, PermissionCache cache, ErrorWriter errWriter) {
        this.jwt = jwt;
        this.cache = cache;
        this.errWriter = errWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            RequestContext.setTraceId(request.getHeader("X-Trace-Id"));

            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                chain.doFilter(request, response);
                return;
            }
            String token = header.substring(7);
            log.info("JwtAuthFilter: token len={}", token.length());
            Claims claims = jwt.parse(token);
            log.info("JwtAuthFilter: parse result claims={}", claims != null);
            if (claims == null) {
                errWriter.write(response, ErrorCodes.UNAUTHENTICATED, "JWT invalid");
                return;
            }
            long userId = Long.parseLong(claims.getSubject());
            long ver    = claims.get("ver", Long.class);

            com.example.rbac.api.identity.IdentitySnapshot snap = cache.getIdentity(userId);
            if (snap == null) {
                errWriter.write(response, ErrorCodes.TOKEN_REVOKED, "identity missing or disabled");
                return;
            }
            if (!snap.enabled()) {
                errWriter.write(response, ErrorCodes.UNAUTHENTICATED, "account disabled");
                return;
            }
            if (snap.tokenVersion() != ver) {
                errWriter.write(response, ErrorCodes.TOKEN_REVOKED, "token version mismatch");
                return;
            }

            RequestContext.setCurrentUser(new CurrentUser(userId, ver));
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }
}
