package com.example.rbac.core.internal.mapper;

import com.example.rbac.core.internal.domain.RoleEntity;
import com.example.rbac.core.internal.domain.UserRoleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserRoleMapper {

    List<UserRoleRow> findAll();

    List<Long> findRoleIdsByUserId(@Param("userId") long userId);

    List<RoleEntity> findRolesByUserId(@Param("userId") long userId);

    int deleteByUserId(@Param("userId") long userId);

    int deleteByRoleId(@Param("roleId") long roleId);

    int insert(@Param("userId") long userId,
               @Param("roleId") long roleId,
               @Param("operatorId") Long operatorId,
               @Param("now") LocalDateTime now);

    long countByRoleId(@Param("roleId") long roleId);

    long countByUserId(@Param("userId") long userId);
}
