package com.example.rbac.config;

import com.example.rbac.api.support.ClientIpResolver;
import com.example.rbac.api.support.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Creates a trace id per request, writes it to the response header and MDC, and
 * publishes the request facts used by audit and rate limiting.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final ClientIpResolver clientIpResolver;

    public TraceIdFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = nextTraceId();
        String clientIp = clientIpResolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        response.setHeader(RequestContext.TRACE_ID_HEADER, traceId);
        MDC.put("traceId", traceId);
        RequestContext.set(new RequestContext.Snapshot(null, null, clientIp,
                request.getHeader("User-Agent"), traceId));
        try {
            log.debug("request {} {} from {} ({})", request.getMethod(), request.getRequestURI(),
                    clientIp, request.getRemoteAddr());
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.remove("traceId");
        }
    }

    private String nextTraceId() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(chars);
    }
}
