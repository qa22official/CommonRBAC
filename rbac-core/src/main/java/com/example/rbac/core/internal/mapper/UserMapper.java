package com.example.rbac.core.internal.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.rbac.core.internal.domain.User;

@Mapper
public interface UserMapper {

    Optional<User> findById(@Param("id") long id);

    Optional<User> findByUsername(@Param("username") String username);

    List<User> list(@Param("keyword") String keyword,
                    @Param("status") Integer status,
                    @Param("offset") int offset,
                    @Param("limit") int limit);

    int count(@Param("keyword") String keyword, @Param("status") Integer status);

    int insert(User user);

    int update(User user);

    /** 逻辑删除 / 停用。status=2 停用、status=3 删除。 */
    int updateStatus(@Param("id") long id, @Param("status") int status);

    /** token_version +1，用于令牌作废。 */
    int bumpTokenVersion(@Param("id") long id);

    /** 改密码 + token_version +1。 */
    int updatePasswordAndBump(@Param("id") long id, @Param("passwordHash") String passwordHash);

    long maxId();

    /** FOR UPDATE 锁行（覆盖式授权并发保护）。 */
    Optional<User> findByIdForUpdate(@Param("id") long id);
}
