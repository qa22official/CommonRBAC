package com.example.rbac.audit.internal;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * AuditRecorder 实现：CHANGE 类与调用者同一事务（由 @Transactional 托管）；
 * SECURITY 类独立落库，失败不回滚业务但计数 + 告警。
 */
@Component
public class AuditRecorderImpl implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorderImpl.class);
    private final AuditMapper mapper;
    private final Counter securityFailCounter;
    private final Executor asyncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rbac-audit-security"); t.setDaemon(true); return t;
    });

    public AuditRecorderImpl(AuditMapper mapper, MeterRegistry metrics) {
        this.mapper = mapper;
        this.securityFailCounter = Counter.builder("rbac.audit.security.fail").register(metrics);
    }

    @Override
    public void record(AuditRecord record) {
        if (record == null) return;
        if (record.category() == AuditCategory.CHANGE) {
            // 同一线程/事务里同步落库；业务 @Transactional 提交时一起提交
            try { mapper.insert(record); }
            catch (Exception e) {
                throw new RuntimeException("CHANGE audit write failed", e);
            }
        } else {
            // SECURITY 类独立异步；失败只计数
            asyncExecutor.execute(() -> {
                try { mapper.insert(record); }
                catch (Exception e) {
                    securityFailCounter.increment();
                    log.warn("SECURITY audit write failed: {}", e.getMessage());
                }
            });
        }
    }
}
