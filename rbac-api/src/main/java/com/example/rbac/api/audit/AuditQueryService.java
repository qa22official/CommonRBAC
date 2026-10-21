package com.example.rbac.api.audit;

public interface AuditQueryService {

    AuditLogPage query(AuditLogQuery query);
}
