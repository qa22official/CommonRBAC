package com.example.rbac.api.audit;

import java.util.List;

public record AuditLogPage(List<AuditLogItem> records, Long nextLastId) {

    public AuditLogPage {
        records = List.copyOf(records);
    }
}
