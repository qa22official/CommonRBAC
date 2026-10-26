package com.example.rbac.core.internal.mapper;

import com.example.rbac.core.internal.domain.RoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RoleMapper {

    List<RoleEntity> findPage(@Param("keyword") String keyword,
                              @Param("status") Integer status,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword, @Param("status") Integer status);

    List<RoleEntity> findEnabledOptions();

    List<RoleEntity> findAll();

    /**
     * Serializes role-inheritance writes. Two concurrent edits of different
     * roles could otherwise each validate against the pre-commit state and
     * together create a cycle (05 §4.3). Role writes are rare, so a single
     * stable lock is cheaper than per-chain locking and deadlock analysis.
     */
    List<Long> lockAllForInheritance();

    RoleEntity findById(@Param("id") long id);

    RoleEntity findByIdForUpdate(@Param("id") long id);

    RoleEntity findByCode(@Param("code") String code);

    int insert(RoleEntity role);

    int update(@Param("id") long id,
               @Param("name") String name,
               @Param("description") String description,
               @Param("sort") int sort,
               @Param("inheritRoleId") long inheritRoleId,
               @Param("operatorId") long operatorId,
               @Param("now") LocalDateTime now);

    int logicalDelete(@Param("id") long id,
                      @Param("operatorId") long operatorId,
                      @Param("now") LocalDateTime now);

    int updateStatus(@Param("id") long id,
                     @Param("status") int status,
                     @Param("operatorId") long operatorId,
                     @Param("now") LocalDateTime now);

    int incrementPermVersion(@Param("id") long id, @Param("now") LocalDateTime now);

    long countByInheritRoleId(@Param("roleId") long roleId);
}
