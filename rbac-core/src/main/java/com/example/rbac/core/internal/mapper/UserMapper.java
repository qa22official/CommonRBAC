package com.example.rbac.core.internal.mapper;

import com.example.rbac.core.internal.domain.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {

    UserEntity findByUsername(@Param("username") String username);

    UserEntity findById(@Param("id") long id);

    UserEntity findByIdForUpdate(@Param("id") long id);

    List<UserEntity> findPage(@Param("keyword") String keyword,
                              @Param("status") Integer status,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword, @Param("status") Integer status);

    int insert(UserEntity user);

    int updateProfile(@Param("id") long id,
                      @Param("nickname") String nickname,
                      @Param("email") String email,
                      @Param("phone") String phone,
                      @Param("remark") String remark,
                      @Param("operatorId") long operatorId,
                      @Param("now") LocalDateTime now);

    int logicalDelete(@Param("id") long id,
                      @Param("operatorId") long operatorId,
                      @Param("now") LocalDateTime now);

    int updateStatus(@Param("id") long id,
                     @Param("status") int status,
                     @Param("operatorId") long operatorId,
                     @Param("now") LocalDateTime now);

    int updatePassword(@Param("id") long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("mustChange") boolean mustChange,
                       @Param("operatorId") long operatorId,
                       @Param("now") LocalDateTime now);

    /**
     * Account-level revocation: increments the watermark and clears the forced
     * password-change flag only when {@code clearMustChange} is true.
     */
    int incrementTokenVersion(@Param("id") long id,
                              @Param("clearMustChange") boolean clearMustChange,
                              @Param("now") LocalDateTime now);

    int incrementRoleVersion(@Param("id") long id, @Param("now") LocalDateTime now);

    int recordLoginFailure(@Param("id") long id,
                           @Param("threshold") int threshold,
                           @Param("lockedUntil") LocalDateTime lockedUntil,
                           @Param("now") LocalDateTime now);

    int clearLoginFailure(@Param("id") long id,
                          @Param("now") LocalDateTime now,
                          @Param("lastLoginAt") LocalDateTime lastLoginAt);

    int resetLoginFailures(@Param("id") long id, @Param("now") LocalDateTime now);

    long countEnabledSuperAdmins();

    long countByRoleCode(@Param("userId") long userId, @Param("roleCode") String roleCode);
}
