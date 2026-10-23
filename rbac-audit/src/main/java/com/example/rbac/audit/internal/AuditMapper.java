package com.example.rbac.audit.internal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;

import java.util.List;

@Mapper
public interface AuditMapper {
    int insert(AuditRecord record);
    List<AuditRecord> query(@Param("category") AuditCategory category,
                            @Param("eventType") String eventType,
                            @Param("operatorId") Long operatorId,
                            @Param("targetType") String targetType,
                            @Param("from") java.time.Instant from,
                            @Param("to") java.time.Instant to,
                            @Param("cursor") String cursor,
                            @Param("limit") int limit);
}
