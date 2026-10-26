package com.example.rbac.core.internal.support;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.api.support.RequestContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes CHANGE audit records inside the caller's business transaction. Audit
 * failure propagates, which rolls the business change back (01 §4.5).
 */
@Component
public class CoreAuditor {

    private final AuditRecorder recorder;

    public CoreAuditor(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * SECURITY events are written independently by the recorder (REQUIRES_NEW)
     * and never roll the business transaction back (01 FR-L4/FR-L5). Used for
     * account-level token revocation triggered by an administrator, which is a
     * SECURITY event in 01 §4.5.
     */
    public void security(long operatorId, String action, String targetType, Long targetId,
                         String detailAfter) {
        String operatorName = RequestContext.username();
        if (operatorName == null || operatorName.isBlank()) {
            operatorName = operatorId > 0 ? "user#" + operatorId : "system";
        }
        String clientIp = RequestContext.clientIp();
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "internal";
        }
        recorder.record(new AuditRecord(
                AuditCategory.SECURITY,
                operatorId > 0 ? operatorId : null,
                operatorName,
                action,
                targetType,
                targetId,
                null,
                detailAfter,
                true,
                null,
                clientIp,
                RequestContext.userAgent(),
                RequestContext.traceId(),
                null,
                Instant.now(),
                null));
    }

    public void change(long operatorId, String action, String targetType, Long targetId,
                       Object before, Object after) {
        String operatorName = RequestContext.username();
        if (operatorName == null || operatorName.isBlank()) {
            operatorName = operatorId > 0 ? "user#" + operatorId : "system";
        }
        String clientIp = RequestContext.clientIp();
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "internal";
        }
        String traceId = RequestContext.traceId();
        String transactionId = traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
        recorder.record(new AuditRecord(
                AuditCategory.CHANGE,
                operatorId > 0 ? operatorId : null,
                operatorName,
                action,
                targetType,
                targetId,
                JsonSupport.toJson(before),
                JsonSupport.toJson(after),
                true,
                null,
                clientIp,
                RequestContext.userAgent(),
                traceId,
                transactionId,
                Instant.now(),
                null));
    }
}
