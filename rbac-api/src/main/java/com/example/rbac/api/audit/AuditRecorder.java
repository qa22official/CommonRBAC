package com.example.rbac.api.audit;

public interface AuditRecorder {

    void record(AuditRecord record);
}
