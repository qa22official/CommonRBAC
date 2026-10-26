package com.example.rbac.guard;

import com.example.rbac.api.auth.TokenVerifier;
import com.example.rbac.api.auth.VerifiedToken;
import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.identity.IdentityQuery;
import com.example.rbac.api.identity.IdentitySnapshot;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ClientIpResolver;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Step 1 of the fixed authorization chain (02 §10): verify the JWT, compare its
 * version with the identity snapshot, and populate the request context. It never
 * rejects directly; the interceptor applies the annotation and default-deny
 * rules, so public endpoints can ignore an invalid header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenVerifier tokenVerifier;
    private final IdentityQuery identityQuery;
    private final ClientIpResolver clientIpResolver;

    public JwtAuthFilter(TokenVerifier tokenVerifier,
                         IdentityQuery identityQuery,
                         ClientIpResolver clientIpResolver) {
        this.tokenVerifier = tokenVerifier;
        this.identityQuery = identityQuery;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (RequestContext.get() == null) {
            RequestContext.set(new RequestContext.Snapshot(
                    null,
                    null,
                    clientIpResolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For")),
                    request.getHeader("User-Agent"),
                    response.getHeader(RequestContext.TRACE_ID_HEADER)));
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            request.setAttribute(RequestContext.AUTH_ERROR_ATTRIBUTE,
                    new BusinessException(ErrorCodes.TOKEN_INVALID, "未登录"));
            filterChain.doFilter(request, response);
            return;
        }
        try {
            VerifiedToken token = tokenVerifier.verify(authorization.substring(7).trim());
            Optional<IdentitySnapshot> identity = identityQuery.findByUserId(token.userId());
            if (identity.isEmpty()) {
                throw new BusinessException(ErrorCodes.TOKEN_REVOKED, "登录状态已失效，请重新登录");
            }
            IdentitySnapshot snapshot = identity.get();
            if (!snapshot.enabled() || snapshot.tokenVersion() != token.tokenVersion()) {
                throw new BusinessException(ErrorCodes.TOKEN_REVOKED, "登录状态已失效，请重新登录");
            }
            CurrentUser currentUser = new CurrentUser(token.userId(), token.tokenVersion(), token.username());
            request.setAttribute(RequestContext.CURRENT_USER_ATTRIBUTE, currentUser);
            request.setAttribute(RequestContext.MUST_CHANGE_ATTRIBUTE, snapshot.mustChangePassword());
            RequestContext.setUser(currentUser.userId(), currentUser.username());
        } catch (BusinessException ex) {
            request.setAttribute(RequestContext.AUTH_ERROR_ATTRIBUTE, ex);
        } catch (RuntimeException ex) {
            request.setAttribute(RequestContext.AUTH_ERROR_ATTRIBUTE,
                    new BusinessException(ErrorCodes.SERVICE_DEGRADED, "鉴权数据暂不可用"));
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}
