package com.example.rbac.audit.internal.service;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.audit.internal.domain.AuditLogEntity;
import com.example.rbac.audit.internal.mapper.AuditLogMapper;
import com.example.rbac.audit.internal.support.AuditRedactor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * CHANGE records are inserted directly into the caller's transaction so an
 * audit failure rolls the business change back. SECURITY records use a
 * REQUIRES_NEW transaction; failures are counted and never propagate (01 §4.5).
 */
@Service
public class AuditRecorderImpl implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorderImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogMapper mapper;
    private final TransactionTemplate requiresNew;
    private final Counter securityFailures;
    private final Counter securityDropped;

    public AuditRecorderImpl(AuditLogMapper mapper,
                             PlatformTransactionManager transactionManager,
                             MeterRegistry meterRegistry) {
        this.mapper = mapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.securityFailures = Counter.builder("rbac.audit.security.failures")
                .description("SECURITY audit write failures")
                .register(meterRegistry);
        this.securityDropped = Counter.builder("rbac.audit.security.dropped")
                .description("SECURITY audit records dropped after a failed write")
                .register(meterRegistry);
    }

    @Override
    public void record(AuditRecord record) {
        AuditLogEntity entity = toEntity(record);
        if (record.category() == AuditCategory.CHANGE) {
            mapper.insert(entity);
            return;
        }
        try {
            requiresNew.executeWithoutResult(status -> mapper.insert(entity));
        } catch (RuntimeException ex) {
            securityFailures.increment();
            securityDropped.increment();
            log.error("SECURITY audit write failed and was dropped: action={}, traceId={}, type={}",
                    record.action(), record.traceId(), ex.getClass().getName());
        }
    }

    private AuditLogEntity toEntity(AuditRecord record) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAuditCategory(record.category().name());
        entity.setOperatorId(record.operatorId());
        entity.setOperatorName(truncate(record.operatorName(), 64));
        entity.setAction(truncate(record.action(), 64));
        entity.setTargetType(truncate(record.targetType(), 32));
        entity.setTargetId(record.targetId());
        entity.setDetailBefore(AuditRedactor.redact(record.detailBefore()));
        entity.setDetailAfter(AuditRedactor.redact(renderDetailAfter(record)));
        entity.setResult(record.success() ? 1 : 0);
        entity.setFailReason(truncate(record.failReason(), 255));
        entity.setTransactionId(truncate(record.transactionId(), 64));
        entity.setClientIp(truncate(record.clientIp(), 64));
        entity.setUserAgent(truncate(record.userAgent(), 512));
        entity.setTraceId(truncate(record.traceId(), 64));
        entity.setEventTime(record.eventTime().atZone(ZoneOffset.UTC).toLocalDateTime());
        entity.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    private String renderDetailAfter(AuditRecord record) {
        AuditRecord.ExportDetail export = record.exportDetail();
        if (export == null) {
            return record.detailAfter();
        }
        ObjectNode node = MAPPER.createObjectNode();
        node.put("scope", export.scope().name());
        node.put("resultCount", export.resultCount());
        if (export.condition() == null || export.condition().isBlank()) {
            node.set("condition", MAPPER.createObjectNode());
        } else {
            try {
                node.set("condition", MAPPER.readTree(export.condition()));
            } catch (Exception ex) {
                node.put("condition", export.condition());
            }
        }
        return node.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }


}
