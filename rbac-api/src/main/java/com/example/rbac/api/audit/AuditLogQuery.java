package com.example.rbac.api.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Audit query. Time range is mandatory; paging is cursor-based.
 */
public record AuditLogQuery(
        Instant startTime,
        Instant endTime,
        AuditCategory category,
        Long operatorId,
        String action,
        String targetType,
        Long targetId,
        Boolean success,
        Long lastId,
        int size) {

    public AuditLogQuery {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("size must be between 1 and 200");
        }
    }
}
