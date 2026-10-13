package com.example.rbac.api.audit;

import java.time.Instant;

public record AuditRecord(
        AuditCategory category,
        String action,
        Long operatorId,
        String transactionId,
        Instant eventTime) {
}
