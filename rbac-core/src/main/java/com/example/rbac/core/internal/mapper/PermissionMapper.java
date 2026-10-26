package com.example.rbac.core.internal.mapper;

import com.example.rbac.core.internal.domain.PermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface PermissionMapper {

    List<PermissionEntity> findAll();

    List<PermissionEntity> findAllEnabled();

    PermissionEntity findById(@Param("id") long id);

    PermissionEntity findByIdForUpdate(@Param("id") long id);

    PermissionEntity findByCode(@Param("code") String code);

    List<PermissionEntity> findByCodes(@Param("codes") Collection<String> codes);

    int insert(PermissionEntity permission);

    int update(PermissionEntity permission);

    int logicalDelete(@Param("id") long id, @Param("now") LocalDateTime now);

    long countChildren(@Param("parentId") long parentId);
}
