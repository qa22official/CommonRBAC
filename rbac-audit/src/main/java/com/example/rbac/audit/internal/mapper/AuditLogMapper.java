package com.example.rbac.audit.internal.mapper;

import com.example.rbac.audit.internal.domain.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuditLogMapper {

    int insert(AuditLogEntity entity);

    List<AuditLogEntity> query(@Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime,
                               @Param("category") String category,
                               @Param("operatorId") Long operatorId,
                               @Param("action") String action,
                               @Param("targetType") String targetType,
                               @Param("targetId") Long targetId,
                               @Param("result") Integer result,
                               @Param("lastId") Long lastId,
                               @Param("limit") int limit);
}
