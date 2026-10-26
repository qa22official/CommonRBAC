package com.example.rbac.core.internal.mapper;

import com.example.rbac.core.internal.domain.RolePermissionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RolePermissionMapper {

    List<RolePermissionRow> findAllEnabled();

    List<String> findCodesByRoleId(@Param("roleId") long roleId);

    List<Long> findPermissionIdsByRoleId(@Param("roleId") long roleId);

    int deleteByRoleId(@Param("roleId") long roleId);

    int insert(@Param("roleId") long roleId,
               @Param("permissionId") long permissionId,
               @Param("operatorId") Long operatorId,
               @Param("now") LocalDateTime now);

    long countByRoleId(@Param("roleId") long roleId);

    long countByPermissionId(@Param("permissionId") long permissionId);
}
