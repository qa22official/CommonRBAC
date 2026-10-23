package com.example.rbac.core.internal.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.rbac.core.internal.domain.Role;

@Mapper
public interface RoleMapper {

    Optional<Role> findById(@Param("id") long id);

    Optional<Role> findByCode(@Param("code") String code);

    /** 把 userId 绑定的全部角色（含停用）拉出来，由上层过滤停用。 */
    List<Role> findByUserId(@Param("userId") long userId);

    List<Role> list(@Param("keyword") String keyword,
                    @Param("status") Integer status,
                    @Param("offset") int offset,
                    @Param("limit") int limit);

    int count(@Param("keyword") String keyword, @Param("status") Integer status);

    int insert(Role role);

    int update(Role role);

    int updateStatus(@Param("id") long id, @Param("status") int status);

    int bumpPermVersion(@Param("id") long id);

    /** FOR UPDATE 锁行，用于覆盖式授权前的并发保护。 */
    Optional<Role> findByIdForUpdate(@Param("id") long id);

    /** 当前系统中启用的超级管理员数量（用于 FR-G2）。 */
    int countEnabledSuperAdmin();
}
