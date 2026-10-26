package com.example.rbac.audit.internal.service;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditLogItem;
import com.example.rbac.api.audit.AuditLogPage;
import com.example.rbac.api.audit.AuditLogQuery;
import com.example.rbac.api.audit.AuditQueryService;
import com.example.rbac.audit.internal.domain.AuditLogEntity;
import com.example.rbac.audit.internal.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AuditQueryServiceImpl implements AuditQueryService {

    private final AuditLogMapper mapper;

    public AuditQueryServiceImpl(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AuditLogPage query(AuditLogQuery query) {
        List<AuditLogEntity> rows = mapper.query(
                toUtc(query.startTime()),
                toUtc(query.endTime()),
                query.category() == null ? null : query.category().name(),
                query.operatorId(),
                query.action(),
                query.targetType(),
                query.targetId(),
                query.success() == null ? null : (query.success() ? 1 : 0),
                query.lastId(),
                query.size() + 1);
        boolean hasMore = rows.size() > query.size();
        List<AuditLogItem> records = rows.stream()
                .limit(query.size())
                .map(this::toItem)
                .toList();
        Long nextLastId = hasMore && !records.isEmpty() ? records.get(records.size() - 1).id() : null;
        return new AuditLogPage(records, nextLastId);
    }

    private AuditLogItem toItem(AuditLogEntity entity) {
        return new AuditLogItem(
                entity.getId(),
                AuditCategory.valueOf(entity.getAuditCategory()),
                entity.getOperatorId(),
                entity.getOperatorName(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                unwrapJson(entity.getDetailBefore()),
                unwrapJson(entity.getDetailAfter()),
                entity.getResult() != null && entity.getResult() == 1,
                entity.getFailReason(),
                entity.getClientIp(),
                entity.getUserAgent(),
                entity.getTraceId(),
                entity.getTransactionId(),
                entity.getEventTime().toInstant(ZoneOffset.UTC),
                entity.getCreatedAt().toInstant(ZoneOffset.UTC));
    }

    private LocalDateTime toUtc(java.time.Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * MySQL's JSON column returns the document text; H2's JSON type wraps a
     * value inserted as text in an extra JSON string. Unwrapping one string
     * layer keeps the API identical on both engines used by this project.
     */
    private String unwrapJson(String value) {
        if (value == null || value.length() < 2 || !value.startsWith("\"") || !value.endsWith("\"")) {
            return value;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value, String.class);
        } catch (Exception ex) {
            return value;
        }
    }
}
