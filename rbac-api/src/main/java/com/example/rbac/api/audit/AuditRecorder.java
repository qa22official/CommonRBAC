package com.example.rbac.api.audit;

/**
 * CHANGE records participate in the caller's transaction; SECURITY records are
 * written independently. Implementations must not mix the two semantics.
 */
public interface AuditRecorder {

    void record(AuditRecord record);
}
