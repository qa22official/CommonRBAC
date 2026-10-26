package com.example.rbac.core.internal.service;

import com.example.rbac.api.dto.PageResult;
import com.example.rbac.api.dto.RoleSummary;
import com.example.rbac.api.dto.user.AssignRolesCommand;
import com.example.rbac.api.dto.user.ChangeUserStatusCommand;
import com.example.rbac.api.dto.user.CreateUserCommand;
import com.example.rbac.api.dto.user.ResetPasswordCommand;
import com.example.rbac.api.dto.user.UpdateUserCommand;
import com.example.rbac.api.dto.user.UserListItem;
import com.example.rbac.api.dto.user.UserProfile;
import com.example.rbac.api.dto.user.UserQuery;
import com.example.rbac.api.dto.user.UserRoles;
import com.example.rbac.api.event.PermissionChangedEvent;
import com.example.rbac.api.event.TokenRevokedEvent;
import com.example.rbac.api.service.UserAdminService;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.core.internal.domain.RoleEntity;
import com.example.rbac.core.internal.domain.UserEntity;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.mapper.UserMapper;
import com.example.rbac.core.internal.mapper.UserRoleMapper;
import com.example.rbac.core.internal.support.CoreAuditor;
import com.example.rbac.core.internal.support.DomainEvents;
import com.example.rbac.core.internal.support.PageSupport;
import com.example.rbac.core.internal.support.TimeSupport;
import com.example.rbac.core.internal.validator.AuthorizationCommand;
import com.example.rbac.core.internal.validator.ConstraintValidatorChain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserAdminServiceImpl implements UserAdminService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,64}$");
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_DELETED = 2;

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final ConstraintValidatorChain validatorChain;
    private final CoreAuditor auditor;
    private final DomainEvents domainEvents;

    public UserAdminServiceImpl(UserMapper userMapper,
                                UserRoleMapper userRoleMapper,
                                RoleMapper roleMapper,
                                ConstraintValidatorChain validatorChain,
                                CoreAuditor auditor,
                                DomainEvents domainEvents) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.validatorChain = validatorChain;
        this.auditor = auditor;
        this.domainEvents = domainEvents;
    }

    @Override
    @Transactional
    public PageResult<UserListItem> list(UserQuery query) {
        PageSupport.PageSpec page = PageSupport.normalize(query.page(), query.size());
        String keyword = normalizeKeyword(query.keyword());
        List<UserListItem> items = userMapper.findPage(keyword, query.status(), page.offset(), page.size())
                .stream()
                .map(this::toListItem)
                .toList();
        long total = userMapper.countPage(keyword, query.status());
        return new PageResult<>(items, total, page.page(), page.size());
    }

    @Override
    @Transactional
    public Optional<UserProfile> findProfile(long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() == STATUS_DELETED) {
            return Optional.empty();
        }
        List<RoleSummary> roles = userRoleMapper.findRolesByUserId(userId).stream()
                .map(role -> new RoleSummary(role.getId(), role.getCode(), role.getName()))
                .toList();
        return Optional.of(new UserProfile(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getRemark(),
                TimeSupport.toInstant(user.getCreatedAt()),
                roles));
    }

    @Override
    @Transactional
    public long create(CreateUserCommand command) {
        if (command.username() == null || !USERNAME.matcher(command.username()).matches()) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID,
                    "账号需为 3-64 位字母、数字、下划线、点或连字符");
        }
        if (userMapper.findByUsername(command.username()) != null) {
            throw new BusinessException(ErrorCodes.UNIQUE_CONFLICT, "该账号已存在");
        }
        LocalDateTime now = TimeSupport.nowUtc();
        UserEntity user = new UserEntity();
        user.setUsername(command.username());
        user.setPasswordHash(command.initialPasswordHash());
        user.setNickname(command.nickname());
        user.setEmail(command.email());
        user.setPhone(command.phone());
        int status = command.status() == null ? STATUS_ENABLED : command.status();
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "status 只能是 0 或 1");
        }
        user.setStatus(status);
        user.setTokenVersion(0);
        user.setMustChangePassword(1);
        user.setRoleVersion(0);
        user.setFailCount(0);
        user.setRemark(command.remark());
        user.setCreatedBy(command.operatorId());
        user.setCreatedAt(now);
        user.setUpdatedBy(command.operatorId());
        user.setUpdatedAt(now);
        userMapper.insert(user);
        auditor.change(command.operatorId(), "USER_CREATE", "USER", user.getId(),
                null, userSnapshot(user));
        return user.getId();
    }

    @Override
    @Transactional
    public void update(UpdateUserCommand command) {
        UserEntity before = requireUser(command.userId());
        userMapper.updateProfile(command.userId(), command.nickname(), command.email(),
                command.phone(), command.remark(), command.operatorId(), TimeSupport.nowUtc());
        UserEntity after = userMapper.findById(command.userId());
        auditor.change(command.operatorId(), "USER_UPDATE", "USER", command.userId(),
                userSnapshot(before), userSnapshot(after));
    }

    @Override
    @Transactional
    public void delete(long operatorId, long userId) {
        UserEntity user = userMapper.findByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() == STATUS_DELETED) {
            return;
        }
        AuthorizationCommand command = new AuthorizationCommand(operatorId,
                AuthorizationCommand.TargetType.USER_STATUS, userId, 0, Set.of(), Set.of(), STATUS_DELETED);
        validatorChain.validate(command);
        LocalDateTime now = TimeSupport.nowUtc();
        userMapper.logicalDelete(userId, operatorId, now);
        userRoleMapper.deleteByUserId(userId);
        userMapper.incrementTokenVersion(userId, false, now);
        long newVersion = currentTokenVersion(userId);
        auditor.change(operatorId, "USER_DELETE", "USER", userId, userSnapshot(user), null);
        auditor.security(operatorId, "TOKEN_REVOKED", "USER", userId,
                "{\"tokenVersion\":" + newVersion + "}");
        domainEvents.publishAfterCommit(new TokenRevokedEvent(userId, newVersion));
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    @Override
    @Transactional
    public void changeStatus(ChangeUserStatusCommand command) {
        if (command.status() != STATUS_ENABLED && command.status() != STATUS_DISABLED) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "status 只能是 0 或 1");
        }
        UserEntity user = userMapper.findByIdForUpdate(command.userId());
        if (user == null || user.getStatus() == STATUS_DELETED) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() == command.status()) {
            return;
        }
        validatorChain.validate(new AuthorizationCommand(command.operatorId(),
                AuthorizationCommand.TargetType.USER_STATUS, command.userId(), 0,
                Set.of(), Set.of(), command.status()));
        LocalDateTime now = TimeSupport.nowUtc();
        userMapper.updateStatus(command.userId(), command.status(), command.operatorId(), now);
        if (command.status() == STATUS_ENABLED) {
            userMapper.resetLoginFailures(command.userId(), now);
        }
        Map<String, Object> before = Map.of("status", user.getStatus());
        Map<String, Object> after = Map.of("status", command.status());
        auditor.change(command.operatorId(), "USER_STATUS", "USER", command.userId(), before, after);
        if (command.status() == STATUS_DISABLED) {
            userMapper.incrementTokenVersion(command.userId(), false, now);
            long newVersion = currentTokenVersion(command.userId());
            auditor.security(command.operatorId(), "TOKEN_REVOKED", "USER", command.userId(),
                    "{\"tokenVersion\":" + newVersion + "}");
            domainEvents.publishAfterCommit(new TokenRevokedEvent(command.userId(), newVersion));
        }
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        UserEntity user = requireUser(command.userId());
        LocalDateTime now = TimeSupport.nowUtc();
        userMapper.updatePassword(command.userId(), command.newPasswordHash(), true,
                command.operatorId(), now);
        userMapper.resetLoginFailures(command.userId(), now);
        userMapper.incrementTokenVersion(command.userId(), false, now);
        long newVersion = currentTokenVersion(command.userId());
        auditor.change(command.operatorId(), "USER_RESET_PASSWORD", "USER", command.userId(),
                Map.of("tokenVersion", user.getTokenVersion()),
                Map.of("mustChangePassword", true, "tokenVersion", newVersion));
        auditor.security(command.operatorId(), "TOKEN_REVOKED", "USER", command.userId(),
                "{\"tokenVersion\":" + newVersion + "}");
        domainEvents.publishAfterCommit(new TokenRevokedEvent(command.userId(), newVersion));
    }

    @Override
    @Transactional
    public Optional<UserRoles> findRoles(long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null || user.getStatus() == STATUS_DELETED) {
            return Optional.empty();
        }
        return Optional.of(new UserRoles(
                new LinkedHashSet<>(userRoleMapper.findRoleIdsByUserId(userId)),
                user.getRoleVersion() == null ? 0 : user.getRoleVersion()));
    }

    @Override
    @Transactional
    public void assignRoles(AssignRolesCommand command) {
        UserEntity user = userMapper.findByIdForUpdate(command.userId());
        if (user == null || user.getStatus() == STATUS_DELETED) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        int currentVersion = user.getRoleVersion() == null ? 0 : user.getRoleVersion();
        if (currentVersion != command.expectedVersion()) {
            throw new BusinessException(ErrorCodes.VERSION_CONFLICT,
                    "角色集合已被他人修改，请刷新后重试");
        }
        Set<Long> finalRoleIds = new LinkedHashSet<>(command.roleIds());
        for (Long roleId : finalRoleIds) {
            RoleEntity role = roleMapper.findById(roleId);
            if (role == null) {
                throw new BusinessException(ErrorCodes.NOT_FOUND, "角色不存在：" + roleId);
            }
        }
        validatorChain.validate(new AuthorizationCommand(command.operatorId(),
                AuthorizationCommand.TargetType.USER_ROLES, command.userId(), 0,
                finalRoleIds, Set.of(), user.getStatus()));
        List<Long> beforeRoleIds = userRoleMapper.findRoleIdsByUserId(command.userId());
        LocalDateTime now = TimeSupport.nowUtc();
        userRoleMapper.deleteByUserId(command.userId());
        for (Long roleId : finalRoleIds) {
            userRoleMapper.insert(command.userId(), roleId, command.operatorId(), now);
        }
        userMapper.incrementRoleVersion(command.userId(), now);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("roleIds", beforeRoleIds);
        before.put("version", currentVersion);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("roleIds", finalRoleIds);
        after.put("version", currentVersion + 1);
        auditor.change(command.operatorId(), "USER_ASSIGN_ROLE", "USER", command.userId(), before, after);
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    private UserEntity requireUser(long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null || user.getStatus() == STATUS_DELETED) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private long currentTokenVersion(long userId) {
        UserEntity refreshed = userMapper.findById(userId);
        return refreshed == null || refreshed.getTokenVersion() == null ? 0 : refreshed.getTokenVersion();
    }

    private UserListItem toListItem(UserEntity user) {
        return new UserListItem(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getPhone(), user.getStatus(),
                TimeSupport.toInstant(user.getCreatedAt()));
    }

    private Map<String, Object> userSnapshot(UserEntity user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", user.getId());
        snapshot.put("username", user.getUsername());
        snapshot.put("nickname", user.getNickname());
        snapshot.put("email", user.getEmail());
        snapshot.put("phone", user.getPhone());
        snapshot.put("status", user.getStatus());
        snapshot.put("mustChangePassword", user.getMustChangePassword());
        snapshot.put("tokenVersion", user.getTokenVersion());
        snapshot.put("remark", user.getRemark());
        return snapshot;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
