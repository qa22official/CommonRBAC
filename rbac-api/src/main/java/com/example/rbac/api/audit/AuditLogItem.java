package com.example.rbac.api.audit;

import java.time.Instant;

public record AuditLogItem(
        long id,
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
        Instant createdAt) {
}
