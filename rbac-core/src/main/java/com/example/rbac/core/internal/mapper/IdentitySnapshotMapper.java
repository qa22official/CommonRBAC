package com.example.rbac.core.internal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.rbac.api.identity.IdentitySnapshot;

/** 仅在缓存未命中时使用的持久化层，供 rbac-cache 调用。 */
@Mapper
public interface IdentitySnapshotMapper {

    IdentitySnapshot findByUserId(@Param("userId") long userId);

    List<Long> listAllUserIds();

    long bumpTokenVersion(@Param("userId") long userId);
}
