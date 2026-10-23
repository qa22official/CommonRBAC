package com.example.rbac.core.internal.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.rbac.core.internal.domain.Permission;

@Mapper
public interface PermissionMapper {

    Optional<Permission> findById(@Param("id") long id);

    Optional<Permission> findByCode(@Param("code") String code);

    /** 把 roleId 绑定的全部权限（含停用）拉出来，由上层过滤停用。 */
    List<Permission> findByRoleId(@Param("roleId") long roleId);

    List<Permission> tree();

    List<Permission> list();

    int insert(Permission permission);

    int update(Permission permission);

    int updateStatus(@Param("id") long id, @Param("status") int status);
}
