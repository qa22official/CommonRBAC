package com.example.rbac.core.internal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RelMapper {

    /** 覆盖式授权：先清空再批量插入（由上层 Service 负责事务）。 */
    int clearUserRoles(@Param("userId") long userId);
    int batchInsertUserRoles(@Param("userId") long userId, @Param("roleIds") Collection<Long> roleIds);

    int clearRolePermissions(@Param("roleId") long roleId);
    int batchInsertRolePermissions(@Param("roleId") long roleId, @Param("permissionIds") Collection<Long> permissionIds);

    /** 查询用户当前角色 ID 集合（含停用角色，由上层过滤）。 */
    List<Long> findRoleIdsByUserId(@Param("userId") long userId);

    /** 查询角色当前权限 ID 集合（含停用权限，由上层过滤）。 */
    List<Long> findPermissionIdsByRoleId(@Param("roleId") long roleId);

    /** 检查是否被任何用户绑定（用于删除前校验）。 */
    int countUsersWithRole(@Param("roleId") long roleId);

    /** 检查是否被任何角色引用。 */
    int countRolesWithPermission(@Param("permissionId") long permissionId);
}
