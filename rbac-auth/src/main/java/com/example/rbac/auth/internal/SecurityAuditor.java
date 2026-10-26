package com.example.rbac.auth.internal;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.api.support.RequestContext;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * SECURITY events are written independently of the business transaction; the
 * recorder swallows write failures and counts them (01 FR-L4/FR-L5).
 */
@Component
public class SecurityAuditor {

    private final AuditRecorder recorder;

    public SecurityAuditor(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    public void record(Long operatorId,
                       String operatorName,
                       String action,
                       String targetType,
                       Long targetId,
                       boolean success,
                       String failReason,
                       String detailAfter) {
        String clientIp = RequestContext.clientIp();
        recorder.record(new AuditRecord(
                AuditCategory.SECURITY,
                operatorId,
                operatorName == null || operatorName.isBlank() ? "anonymous" : operatorName,
                action,
                targetType,
                targetId,
                null,
                detailAfter,
                success,
                failReason,
                clientIp == null || clientIp.isBlank() ? "internal" : clientIp,
                RequestContext.userAgent(),
                RequestContext.traceId(),
                null,
                Instant.now(),
                null));
    }

    static String mask(String username) {
        if (username == null || username.isBlank()) {
            return "anonymous";
        }
        if (username.length() <= 2) {
            return "**";
        }
        if (username.length() <= 4) {
            return username.charAt(0) + "**" + username.charAt(username.length() - 1);
        }
        return username.substring(0, 2) + "***" + username.substring(username.length() - 1);
    }
}
