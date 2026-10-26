package com.example.rbac.core.internal.service;

import com.example.rbac.api.dto.permission.CreatePermissionCommand;
import com.example.rbac.api.dto.permission.PermissionDetail;
import com.example.rbac.api.dto.permission.PermissionTreeNode;
import com.example.rbac.api.dto.permission.UpdatePermissionCommand;
import com.example.rbac.api.event.PermissionChangedEvent;
import com.example.rbac.api.service.PermissionAdminService;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import com.example.rbac.core.internal.domain.PermissionEntity;
import com.example.rbac.core.internal.mapper.PermissionMapper;
import com.example.rbac.core.internal.mapper.RolePermissionMapper;
import com.example.rbac.core.internal.support.CoreAuditor;
import com.example.rbac.core.internal.support.DomainEvents;
import com.example.rbac.core.internal.support.PermissionCodeValidator;
import com.example.rbac.core.internal.support.TimeSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PermissionAdminServiceImpl implements PermissionAdminService {

    private static final Set<String> RESOURCE_TYPES = Set.of("MENU", "BUTTON", "API", "SYSTEM");
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DELETED = 2;

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final CoreAuditor auditor;
    private final DomainEvents domainEvents;

    public PermissionAdminServiceImpl(PermissionMapper permissionMapper,
                                      RolePermissionMapper rolePermissionMapper,
                                      CoreAuditor auditor,
                                      DomainEvents domainEvents) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.auditor = auditor;
        this.domainEvents = domainEvents;
    }

    @Override
    @Transactional
    public List<PermissionTreeNode> tree() {
        List<PermissionEntity> all = permissionMapper.findAll();
        Map<Long, List<PermissionEntity>> children = new HashMap<>();
        List<PermissionEntity> roots = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (PermissionEntity permission : all) {
            ids.add(permission.getId());
        }
        for (PermissionEntity permission : all) {
            long parentId = permission.getParentId() == null ? 0L : permission.getParentId();
            if (parentId == 0L || !ids.contains(parentId)) {
                roots.add(permission);
            } else {
                children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(permission);
            }
        }
        Comparator<PermissionEntity> order = Comparator
                .comparing((PermissionEntity p) -> p.getSort() == null ? 0 : p.getSort())
                .thenComparing(PermissionEntity::getId);
        roots.sort(order);
        return roots.stream().map(root -> buildNode(root, children, order, new HashSet<>())).toList();
    }

    private PermissionTreeNode buildNode(PermissionEntity permission,
                                         Map<Long, List<PermissionEntity>> children,
                                         Comparator<PermissionEntity> order,
                                         Set<Long> path) {
        if (!path.add(permission.getId())) {
            return toNode(permission, List.of());
        }
        List<PermissionEntity> childRows = children.getOrDefault(permission.getId(), List.of());
        List<PermissionEntity> sorted = new ArrayList<>(childRows);
        sorted.sort(order);
        List<PermissionTreeNode> nodes = sorted.stream()
                .map(child -> buildNode(child, children, order, path))
                .toList();
        path.remove(permission.getId());
        return toNode(permission, nodes);
    }

    private PermissionTreeNode toNode(PermissionEntity permission, List<PermissionTreeNode> children) {
        return new PermissionTreeNode(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getResourceType(),
                permission.getParentId() == null ? 0L : permission.getParentId(),
                permission.getPath(),
                permission.getHttpMethod(),
                permission.getIcon(),
                permission.getStatus() == null ? 0 : permission.getStatus(),
                permission.getSort() == null ? 0 : permission.getSort(),
                children);
    }

    @Override
    @Transactional
    public Optional<PermissionDetail> findDetail(long permissionId) {
        PermissionEntity permission = permissionMapper.findById(permissionId);
        if (permission == null) {
            return Optional.empty();
        }
        return Optional.of(new PermissionDetail(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getResourceType(),
                permission.getParentId() == null ? 0L : permission.getParentId(),
                permission.getPath(),
                permission.getHttpMethod(),
                permission.getIcon(),
                permission.getStatus() == null ? 0 : permission.getStatus(),
                permission.getSort() == null ? 0 : permission.getSort(),
                permission.builtin()));
    }

    @Override
    @Transactional
    public long create(CreatePermissionCommand command) {
        PermissionCodeValidator.requireValid(command.code());
        if (permissionMapper.findByCode(command.code()) != null) {
            throw new BusinessException(ErrorCodes.UNIQUE_CONFLICT, "该权限码已存在");
        }
        String resourceType = requireResourceType(command.resourceType());
        long parentId = validateParent(0L, command.parentId());
        LocalDateTime now = TimeSupport.nowUtc();
        PermissionEntity permission = new PermissionEntity();
        permission.setCode(command.code());
        permission.setName(command.name());
        permission.setResourceType(resourceType);
        permission.setParentId(parentId);
        permission.setPath(command.path());
        permission.setHttpMethod(command.httpMethod());
        permission.setIcon(command.icon());
        permission.setStatus(STATUS_ENABLED);
        permission.setIsBuiltin(0);
        permission.setSort(command.sort() == null ? 0 : command.sort());
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        permissionMapper.insert(permission);
        auditor.change(command.operatorId(), "PERM_CREATE", "PERMISSION", permission.getId(),
                null, permissionSnapshot(permission));
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
        return permission.getId();
    }

    @Override
    @Transactional
    public void update(UpdatePermissionCommand command) {
        PermissionEntity permission = permissionMapper.findByIdForUpdate(command.permissionId());
        if (permission == null) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "权限不存在");
        }
        if (command.code() != null && !command.code().equals(permission.getCode())) {
            throw new BusinessException(ErrorCodes.PERMISSION_CODE_IMMUTABLE, "权限码创建后不可修改");
        }
        String resourceType = requireResourceType(command.resourceType());
        int status = command.status() == null ? permission.getStatus() : command.status();
        if (status != 0 && status != 1) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "status 只能是 0 或 1");
        }
        if (permission.builtin() && "*".equals(permission.getCode()) && status != 1) {
            throw new BusinessException(ErrorCodes.BUILTIN_PERMISSION_FORBIDDEN, "内置权限不允许停用");
        }
        long parentId = validateParent(command.permissionId(), command.parentId());
        PermissionEntity before = copy(permission);
        permission.setName(command.name());
        permission.setResourceType(resourceType);
        permission.setParentId(parentId);
        permission.setPath(command.path());
        permission.setHttpMethod(command.httpMethod());
        permission.setIcon(command.icon());
        permission.setStatus(status);
        permission.setSort(command.sort() == null
                ? (permission.getSort() == null ? 0 : permission.getSort()) : command.sort());
        permission.setUpdatedAt(TimeSupport.nowUtc());
        permissionMapper.update(permission);
        auditor.change(command.operatorId(), "PERM_UPDATE", "PERMISSION", command.permissionId(),
                permissionSnapshot(before), permissionSnapshot(permission));
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    @Override
    @Transactional
    public void delete(long operatorId, long permissionId) {
        PermissionEntity permission = permissionMapper.findByIdForUpdate(permissionId);
        if (permission == null || permission.getStatus() == STATUS_DELETED) {
            return;
        }
        if (permission.builtin()) {
            throw new BusinessException(ErrorCodes.BUILTIN_PERMISSION_FORBIDDEN, "内置权限不允许删除");
        }
        long referenced = rolePermissionMapper.countByPermissionId(permissionId);
        if (referenced > 0) {
            throw new BusinessException(ErrorCodes.PERMISSION_REFERENCED,
                    "该权限仍被 " + referenced + " 个角色引用，无法删除");
        }
        long children = permissionMapper.countChildren(permissionId);
        if (children > 0) {
            throw new BusinessException(ErrorCodes.PERMISSION_REFERENCED,
                    "该权限下仍有 " + children + " 个子权限，无法删除");
        }
        permissionMapper.logicalDelete(permissionId, TimeSupport.nowUtc());
        auditor.change(operatorId, "PERM_DELETE", "PERMISSION", permissionId,
                permissionSnapshot(permission), null);
        domainEvents.publishAfterCommit(new PermissionChangedEvent(0));
    }

    private long validateParent(long permissionId, Long requestedParentId) {
        if (requestedParentId == null || requestedParentId == 0L) {
            return 0L;
        }
        if (requestedParentId == permissionId) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "父权限不能是自身");
        }
        PermissionEntity parent = permissionMapper.findById(requestedParentId);
        if (parent == null) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "父权限不存在");
        }
        Set<Long> visited = new HashSet<>();
        long cursor = requestedParentId;
        while (cursor != 0L) {
            if (!visited.add(cursor)) {
                throw new BusinessException(ErrorCodes.PARAM_INVALID, "权限树存在环");
            }
            if (cursor == permissionId) {
                throw new BusinessException(ErrorCodes.PARAM_INVALID, "父权限不能是自身的子节点");
            }
            PermissionEntity node = permissionMapper.findById(cursor);
            if (node == null || node.getParentId() == null) {
                break;
            }
            cursor = node.getParentId();
        }
        return requestedParentId;
    }

    private String requireResourceType(String resourceType) {
        if (resourceType == null || !RESOURCE_TYPES.contains(resourceType)) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID,
                    "resourceType 只能是 MENU、BUTTON、API 或 SYSTEM");
        }
        return resourceType;
    }

    private PermissionEntity copy(PermissionEntity source) {
        PermissionEntity copy = new PermissionEntity();
        copy.setId(source.getId());
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setResourceType(source.getResourceType());
        copy.setParentId(source.getParentId());
        copy.setPath(source.getPath());
        copy.setHttpMethod(source.getHttpMethod());
        copy.setIcon(source.getIcon());
        copy.setStatus(source.getStatus());
        copy.setIsBuiltin(source.getIsBuiltin());
        copy.setSort(source.getSort());
        return copy;
    }

    private Map<String, Object> permissionSnapshot(PermissionEntity permission) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", permission.getId());
        snapshot.put("code", permission.getCode());
        snapshot.put("name", permission.getName());
        snapshot.put("resourceType", permission.getResourceType());
        snapshot.put("parentId", permission.getParentId());
        snapshot.put("path", permission.getPath());
        snapshot.put("httpMethod", permission.getHttpMethod());
        snapshot.put("icon", permission.getIcon());
        snapshot.put("status", permission.getStatus());
        snapshot.put("sort", permission.getSort());
        return snapshot;
    }
}
