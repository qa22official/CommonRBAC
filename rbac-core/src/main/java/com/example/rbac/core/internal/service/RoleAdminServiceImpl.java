package com.example.rbac.core.internal.service;

import com.example.rbac.api.dto.PageResult;
import com.example.rbac.api.dto.RoleSummary;
import com.example.rbac.api.dto.role.AssignPermissionsCommand;
import com.example.rbac.api.dto.role.ChangeRoleStatusCommand;
import com.example.rbac.api.dto.role.CreateRoleCommand;
import com.example.rbac.api.dto.role.RoleDetail;
import com.example.rbac.api.dto.role.RoleListItem;
import com.example.rbac.api.dto.role.RolePermissions;
import com.example.rbac.api.dto.role.RoleQuery;
import com.example.rbac.api.dto.role.RoleTreeNode;
import com.example.rbac.api.dto.role.UpdateRoleCommand;
import com.example.rbac.api.event.PermissionChangedEvent;
import com.example.rbac.api.service.RoleAdminService;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.core.internal.domain.PermissionEntity;
import com.example.rbac.core.internal.domain.RoleEntity;
import com.example.rbac.core.internal.mapper.PermissionMapper;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.mapper.RolePermissionMapper;
import com.example.rbac.core.internal.support.CoreAuditor;
import com.example.rbac.core.internal.support.DomainEvents;
import com.example.rbac.core.internal.support.PageSupport;
import com.example.rbac.core.internal.support.PermissionCodeValidator;
import com.example.rbac.core.internal.support.RoleInheritanceGuard;
import com.example.rbac.core.internal.support.TimeSupport;
import com.example.rbac.core.internal.validator.AuthorizationCommand;
import com.example.rbac.core.internal.validator.ConstraintValidatorChain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RoleAdminServiceImpl implements RoleAdminService {

    private static final Pattern ROLE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_DELETED = 2;

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final RoleInheritanceGuard inheritanceGuard;
    private final ConstraintValidatorChain validatorChain;
    private final CoreAuditor auditor;
    private final DomainEvents domainEvents;

    public RoleAdminServiceImpl(RoleMapper roleMapper,
                                RolePermissionMapper rolePermissionMapper,
                                PermissionMapper permissionMapper,
                                RoleInheritanceGuard inheritanceGuard,
                                ConstraintValidatorChain validatorChain,
                                CoreAuditor auditor,
                                DomainEvents domainEvents) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.inheritanceGuard = inheritanceGuard;
        this.validatorChain = validatorChain;
        this.auditor = auditor;
        this.domainEvents = domainEvents;
    }

    @Override
    @Transactional
    public PageResult<RoleListItem> list(RoleQuery query) {
        PageSupport.PageSpec page = PageSupport.normalize(query.page(), query.size());
        String keyword = normalizeKeyword(query.keyword());
        List<RoleListItem> items = roleMapper.findPage(keyword, query.status(), page.offset(), page.size())
                .stream()
                .map(this::toListItem)
                .toList();
        long total = roleMapper.countPage(keyword, query.status());
        return new PageResult<>(items, total, page.page(), page.size());
    }

    @Override
    @Transactional
    public List<RoleSummary> options() {
        return roleMapper.findEnabledOptions().stream()
                .map(role -> new RoleSummary(role.getId(), role.getCode(), role.getName()))
                .toList();
    }

    @Override
    @Transactional
    public List<RoleTreeNode> tree() {
        List<RoleEntity> roles = roleMapper.findAll();
        Map<Long, RoleEntity> byId = new LinkedHashMap<>();
        for (RoleEntity role : roles) {
            byId.put(role.getId(), role);
        }
        Map<Long, List<RoleEntity>> children = new LinkedHashMap<>();
        List<RoleEntity> roots = new ArrayList<>();
        for (RoleEntity role : roles) {
            long parentId = role.getInheritRoleId() == null ? 0L : role.getInheritRoleId();
            RoleEntity parent = byId.get(parentId);
            if (parentId == 0L || parent == null || parentId == role.getId() || wouldCycle(role.getId(), byId)) {
                roots.add(role);
            } else {
                children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(role);
            }
        }
        Comparator<RoleEntity> order = roleOrder();
        roots.sort(order);
        return roots.stream()
                .map(root -> buildNode(root, byId, children, order, new HashSet<>()))
                .toList();
    }

    private boolean wouldCycle(long roleId, Map<Long, RoleEntity> byId) {
        Set<Long> seen = new HashSet<>();
        long cursor = roleId;
        while (cursor != 0L) {
            if (!seen.add(cursor)) {
                return true;
            }
            RoleEntity role = byId.get(cursor);
            if (role == null) {
                return false;
            }
            cursor = role.getInheritRoleId() == null ? 0L : role.getInheritRoleId();
        }
        return false;
    }

    private RoleTreeNode buildNode(RoleEntity role,
                                   Map<Long, RoleEntity> byId,
                                   Map<Long, List<RoleEntity>> children,
                                   Comparator<RoleEntity> order,
                                   Set<Long> path) {
        if (!path.add(role.getId())) {
            return toTreeNode(role, byId, List.of());
        }
        List<RoleEntity> childRows = new ArrayList<>(children.getOrDefault(role.getId(), List.of()));
        childRows.sort(order);
        List<RoleTreeNode> nodes = childRows.stream()
                .map(child -> buildNode(child, byId, children, order, path))
                .toList();
        path.remove(role.getId());
        return toTreeNode(role, byId, nodes);
    }

    private RoleTreeNode toTreeNode(RoleEntity role,
                                    Map<Long, RoleEntity> byId,
                                    List<RoleTreeNode> children) {
        Long inheritRoleId = role.getInheritRoleId() == null ? 0L : role.getInheritRoleId();
        RoleEntity parent = byId.get(inheritRoleId);
        return new RoleTreeNode(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getStatus(),
                role.builtin(),
                role.getSort() == null ? 0 : role.getSort(),
                inheritRoleId,
                parent == null ? null : parent.getName(),
                children);
    }

    private Comparator<RoleEntity> roleOrder() {
        return Comparator
                .comparingInt((RoleEntity role) -> role.getSort() == null ? 0 : role.getSort())
                .thenComparingLong(RoleEntity::getId);
    }

    @Override
    @Transactional
    public Optional<RoleDetail> findDetail(long roleId) {
        RoleEntity role = roleMapper.findById(roleId);
        if (role == null) {
            return Optional.empty();
        }
        return Optional.of(new RoleDetail(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getStatus(),
                role.builtin(),
                role.getSort() == null ? 0 : role.getSort(),
                role.getPermVersion() == null ? 0 : role.getPermVersion(),
                role.getUserCount() == null ? 0 : role.getUserCount(),
                role.getPermissionCount() == null ? 0 : role.getPermissionCount(),
                role.getInheritRoleId() == null ? 0L : role.getInheritRoleId(),
                role.getInheritRoleName()));
    }

    @Override
    @Transactional
    public long create(CreateRoleCommand command) {
        if (command.code() == null || !ROLE_CODE.matcher(command.code()).matches()) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID,
                    "角色编码需为 2-64 位大写字母、数字或下划线");
        }
        // Serialize inheritance writes so two concurrent edits cannot create a
        // cycle that neither transaction could see (05 §4.3).
        roleMapper.lockAllForInheritance();
        if (roleMapper.findByCode(command.code()) != null) {
            throw new BusinessException(ErrorCodes.UNIQUE_CONFLICT, "该角色编码已存在");
        }
        long inheritRoleId = inheritanceGuard.validate(0L, command.inheritRoleId());
        LocalDateTime now = TimeSupport.nowUtc();
        RoleEntity role = new RoleEntity();
        role.setCode(command.code());
        role.setName(command.name());
        role.setDescription(command.description());
        role.setInheritRoleId(inheritRoleId);
        role.setStatus(STATUS_ENABLED);
        role.setIsBuiltin(0);
        role.setPermVersion(0);
        role.setSort(command.sort() == null ? 0 : command.sort());
        role.setCreatedBy(command.operatorId());
        role.setCreatedAt(now);
        role.setUpdatedBy(command.operatorId());
        role.setUpdatedAt(now);
        roleMapper.insert(role);
        auditor.change(command.operatorId(), "ROLE_CREATE", "ROLE", role.getId(),
                null, roleSnapshot(role));
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
        return role.getId();
    }

    @Override
    @Transactional
    public void update(UpdateRoleCommand command) {
        // Lock all roles before the target row: this serializes all inheritance
        // updates (including their ancestor-chain validation) and avoids the
        // TOCTOU cycle window described in 05 §4.3.
        roleMapper.lockAllForInheritance();
        RoleEntity role = roleMapper.findByIdForUpdate(command.roleId());
        if (role == null) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "角色不存在");
        }
        if (role.builtin()) {
            throw new BusinessException(ErrorCodes.BUILTIN_ROLE_FORBIDDEN, "内置角色不允许此操作");
        }
        // 04 §4.4: 0 and null both mean "do not inherit". Null is what the web
        // form sends when the user clears the selector, so treating null as
        // "keep the old value" made inheritance impossible to remove.
        Long requestedInherit = command.inheritRoleId() == null ? 0L : command.inheritRoleId();
        long inheritRoleId = inheritanceGuard.validate(command.roleId(), requestedInherit);
        LocalDateTime now = TimeSupport.nowUtc();
        int sort = command.sort() == null
                ? (role.getSort() == null ? 0 : role.getSort()) : command.sort();
        roleMapper.update(command.roleId(), command.name(), command.description(),
                sort, inheritRoleId, command.operatorId(), now);
        RoleEntity after = roleMapper.findById(command.roleId());
        auditor.change(command.operatorId(), "ROLE_UPDATE", "ROLE", command.roleId(),
                roleSnapshot(role), roleSnapshot(after));
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    @Override
    @Transactional
    public void delete(long operatorId, long roleId) {
        // Keep the reference check and inheritance writes on the same lock.
        roleMapper.lockAllForInheritance();
        RoleEntity role = roleMapper.findByIdForUpdate(roleId);
        if (role == null || role.getStatus() == STATUS_DELETED) {
            return;
        }
        validatorChain.validate(new AuthorizationCommand(operatorId,
                AuthorizationCommand.TargetType.ROLE_DELETE, 0, roleId, Set.of(), Set.of(), STATUS_DELETED));
        LocalDateTime now = TimeSupport.nowUtc();
        roleMapper.logicalDelete(roleId, operatorId, now);
        auditor.change(operatorId, "ROLE_DELETE", "ROLE", roleId, roleSnapshot(role), null);
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    @Override
    @Transactional
    public void changeStatus(ChangeRoleStatusCommand command) {
        if (command.status() != STATUS_ENABLED && command.status() != STATUS_DISABLED) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "status 只能是 0 或 1");
        }
        RoleEntity role = roleMapper.findByIdForUpdate(command.roleId());
        if (role == null || role.getStatus() == STATUS_DELETED) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "角色不存在");
        }
        if (role.getStatus() == command.status()) {
            return;
        }
        validatorChain.validate(new AuthorizationCommand(command.operatorId(),
                AuthorizationCommand.TargetType.ROLE_STATUS, 0, command.roleId(),
                Set.of(), Set.of(), command.status()));
        roleMapper.updateStatus(command.roleId(), command.status(), command.operatorId(), TimeSupport.nowUtc());
        auditor.change(command.operatorId(), "ROLE_STATUS", "ROLE", command.roleId(),
                Map.of("status", role.getStatus()), Map.of("status", command.status()));
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    @Override
    @Transactional
    public Optional<RolePermissions> findPermissions(long roleId) {
        RoleEntity role = roleMapper.findById(roleId);
        if (role == null) {
            return Optional.empty();
        }
        return Optional.of(new RolePermissions(
                new LinkedHashSet<>(rolePermissionMapper.findCodesByRoleId(roleId)),
                role.getPermVersion() == null ? 0 : role.getPermVersion()));
    }

    @Override
    @Transactional
    public void assignPermissions(AssignPermissionsCommand command) {
        if (command.permissionCodes() == null) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "permissionCodes 不能为空");
        }
        RoleEntity role = roleMapper.findByIdForUpdate(command.roleId());
        if (role == null || role.getStatus() == STATUS_DELETED) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "角色不存在");
        }
        int currentVersion = role.getPermVersion() == null ? 0 : role.getPermVersion();
        if (currentVersion != command.expectedVersion()) {
            throw new BusinessException(ErrorCodes.VERSION_CONFLICT,
                    "权限集合已被他人修改，请刷新后重试");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String code : command.permissionCodes()) {
            PermissionCodeValidator.requireValid(code);
            codes.add(code);
        }
        List<PermissionEntity> found = codes.isEmpty()
                ? List.of()
                : permissionMapper.findByCodes(codes);
        if (found.size() != codes.size()) {
            Set<String> missing = new LinkedHashSet<>(codes);
            for (PermissionEntity permission : found) {
                missing.remove(permission.getCode());
            }
            throw new BusinessException(ErrorCodes.PERMISSION_CODE_INVALID,
                    "存在未定义或已停用的权限码：" + String.join(", ", missing));
        }
        validatorChain.validate(new AuthorizationCommand(command.operatorId(),
                AuthorizationCommand.TargetType.ROLE_PERMISSIONS, 0, command.roleId(),
                Set.of(), codes, role.getStatus()));
        LocalDateTime now = TimeSupport.nowUtc();
        List<String> before = rolePermissionMapper.findCodesByRoleId(command.roleId());
        rolePermissionMapper.deleteByRoleId(command.roleId());
        List<PermissionEntity> ordered = new ArrayList<>(found);
        ordered.sort((a, b) -> a.getCode().compareTo(b.getCode()));
        for (PermissionEntity permission : ordered) {
            rolePermissionMapper.insert(command.roleId(), permission.getId(),
                    command.operatorId(), now);
        }
        roleMapper.incrementPermVersion(command.roleId(), now);
        Map<String, Object> beforeSnapshot = new LinkedHashMap<>();
        beforeSnapshot.put("permissionCodes", before);
        beforeSnapshot.put("version", currentVersion);
        Map<String, Object> afterSnapshot = new LinkedHashMap<>();
        afterSnapshot.put("permissionCodes", codes);
        afterSnapshot.put("version", currentVersion + 1);
        auditor.change(command.operatorId(), "ROLE_PERM_ASSIGN", "ROLE", command.roleId(),
                beforeSnapshot, afterSnapshot);
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    private RoleListItem toListItem(RoleEntity role) {
        return new RoleListItem(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getStatus(), role.builtin(), role.getSort() == null ? 0 : role.getSort(),
                role.getInheritRoleId() == null ? 0L : role.getInheritRoleId(),
                role.getInheritRoleName());
    }

    private Map<String, Object> roleSnapshot(RoleEntity role) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", role.getId());
        snapshot.put("code", role.getCode());
        snapshot.put("name", role.getName());
        snapshot.put("description", role.getDescription());
        snapshot.put("status", role.getStatus());
        snapshot.put("builtin", role.builtin());
        snapshot.put("sort", role.getSort());
        snapshot.put("inheritRoleId", role.getInheritRoleId());
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
