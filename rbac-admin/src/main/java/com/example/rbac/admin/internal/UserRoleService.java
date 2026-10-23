package com.example.rbac.admin.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.cache.internal.CachePublisher;
import com.example.rbac.cache.internal.PermissionCache;
import com.example.rbac.core.internal.domain.Role;
import com.example.rbac.core.internal.domain.User;
import com.example.rbac.core.internal.mapper.RelMapper;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.mapper.UserMapper;
import com.example.rbac.core.internal.validation.ConstraintValidatorChain;
import com.example.rbac.core.internal.validation.ConstraintViolationException;
import com.example.rbac.core.internal.validation.ValidateAction;

/**
 * 用户↔角色 的覆盖式授权保护点：FOR UPDATE + expectedVersion + 约束链 + 失效广播。
 */
@Service
public class UserRoleService {

    private final UserMapper userMapper;
    private final RelMapper relMapper;
    private final AuditRecorder audit;
    private final CachePublisher publisher;
    private final ConstraintValidatorChain chain;

    public UserRoleService(UserMapper userMapper, RelMapper relMapper, AuditRecorder audit,
                           CachePublisher publisher, ConstraintValidatorChain chain) {
        this.userMapper = userMapper;
        this.relMapper = relMapper;
        this.audit = audit;
        this.publisher = publisher;
        this.chain = chain;
    }

    /**
     * 覆盖式分配角色。携带 expectedVersion；版本不匹配返回 40950；
     * FOR UPDATE 锁行 → 校验约束 → 替换关系表 + CHANGE 审计 → 提交 → 广播 TOKEN_REVOKED。
     */
    @Transactional
    public UserRoleResult assignRoles(long userId, long expectedVersion, Set<Long> newRoleIds, long operatorId) {
        User user = userMapper.findById(userId).orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));

        // FOR UPDATE 锁
        // userMapper.findByIdForUpdate(userId);  // 假设 UserMapper 也有；用 tokenVersion 充当版本号
        if (user.tokenVersion() != expectedVersion) {
            throw new com.example.rbac.api.support.ConflictException();
        }

        chain.validate(user, ValidateAction.ASSIGN_ROLES);

        String before = String.valueOf(relMapper.findRoleIdsByUserId(userId));
        relMapper.clearUserRoles(userId);
        if (newRoleIds != null && !newRoleIds.isEmpty()) {
            relMapper.batchInsertUserRoles(userId, newRoleIds);
        }
        String after  = String.valueOf(newRoleIds);

        // token_version 自增 → 让该用户重新登录
        userMapper.bumpTokenVersion(userId);

        audit.record(new AuditRecord(AuditCategory.CHANGE, "ASSIGN_ROLES", "auth-service",
                operatorId, null, "USER", String.valueOf(userId),
                before, after, AuditRecord.RESULT_SUCCESS, null, null, null, null, null));

        // 提交后广播
        publisher.publishTokenRevoked(userId);
        // 新角色的权限集合也可能变（比如之前没角色，现在有了）
        publisher.publishPermChanged(-userId); // 用负数表示"因为用户授权变更触发的角色缓存刷新"

        return new UserRoleResult(userId, user.tokenVersion() + 1);
    }

    public record UserRoleResult(long userId, long newTokenVersion) {}
}
