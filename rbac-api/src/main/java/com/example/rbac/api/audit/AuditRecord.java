package com.example.rbac.api.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit payload carrying every business column of {@code sys_audit_log}.
 * {@code success} maps to {@code result} (true = 1, false = 0).
 * {@code detailBefore}/{@code detailAfter} and {@code ExportDetail.condition}
 * are pre-serialized JSON strings; callers are responsible for redaction.
 */
public record AuditRecord(
        AuditCategory category,
        Long operatorId,
        String operatorName,
        String action,
        String targetType,
        Long targetId,
        String detailBefore,
        String detailAfter,
        boolean success,
        String failReason,
        String clientIp,
        String userAgent,
        String traceId,
        String transactionId,
        Instant eventTime,
        ExportDetail exportDetail) {

    public AuditRecord {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(operatorName, "operatorName");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(clientIp, "clientIp");
        Objects.requireNonNull(eventTime, "eventTime");
    }

    public enum ExportScope {
        FILTERED,
        ALL,
        SINGLE
    }

    /**
     * Export metadata persisted in {@code detail_after}.
     */
    public record ExportDetail(ExportScope scope, long resultCount, String condition) {

        public ExportDetail {
            Objects.requireNonNull(scope, "scope");
        }
    }
}
