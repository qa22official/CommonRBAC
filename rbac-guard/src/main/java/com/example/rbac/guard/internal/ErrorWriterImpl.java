package com.example.rbac.guard.internal;

import java.io.IOException;

import org.springframework.http.MediaType;

import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.api.support.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ErrorWriterImpl implements ErrorWriter {

    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public void write(jakarta.servlet.http.HttpServletResponse response, int code, String message) throws IOException {
        int http = switch (code) {
            case ErrorCodes.UNAUTHENTICATED, ErrorCodes.TOKEN_REVOKED -> 401;
            case ErrorCodes.FORBIDDEN -> 403;
            case ErrorCodes.VERSION_CONFLICT -> 409;
            case ErrorCodes.SERVICE_DEGRADED -> 503;
            default -> 400;
        };
        response.setStatus(http);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OM.writeValueAsString(new ApiResponse<Void>(code, message, null, null)));
    }
}
