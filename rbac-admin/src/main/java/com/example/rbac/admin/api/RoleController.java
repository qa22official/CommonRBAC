package com.example.rbac.admin.api;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.rbac.admin.internal.RolePermService;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.RequestContext;
import com.example.rbac.core.internal.mapper.RelMapper;
import com.example.rbac.core.internal.mapper.RoleMapper;
import com.example.rbac.core.internal.domain.Role;

@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleMapper roleMapper;
    private final RelMapper relMapper;
    private final AuditRecorder audit;
    private final RolePermService rolePermService;

    public RoleController(RoleMapper roleMapper, RelMapper relMapper, AuditRecorder audit, RolePermService rolePermService) {
        this.roleMapper = roleMapper;
        this.relMapper = relMapper;
        this.audit = audit;
        this.rolePermService = rolePermService;
    }

    @RequiresPermission("sys:role:read")
    @GetMapping
    public ApiResponse<List<Role>> list(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(roleMapper.list(keyword, status, page * size, size));
    }

    @RequiresPermission("sys:role:create")
    @PostMapping
    public ApiResponse<Role> create(@RequestBody RoleCreateRequest req) {
        var r = new Role(0L, req.code(), req.name(), req.description(), 1, 0, 0, false, null, null);
        roleMapper.insert(r);
        return ApiResponse.ok(r);
    }

    @RequiresPermission("sys:role:update")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody RoleCreateRequest req) {
        var opt = roleMapper.findById(id);
        if (opt.isEmpty()) return ApiResponse.fail(404, "role not found");
        roleMapper.update(new Role(opt.get().id(), opt.get().code(), req.name(), req.description(),
                opt.get().status(), opt.get().permVersion(), opt.get().inheritRoleId(), opt.get().builtin(),
                opt.get().createdAt(), opt.get().updatedAt()));
        return ApiResponse.ok(null);
    }

    @RequiresPermission("sys:role:update")
    @PutMapping("/{id}/status/{status}")
    public ApiResponse<Void> updateStatus(@PathVariable long id, @PathVariable int status) {
        roleMapper.updateStatus(id, status);
        return ApiResponse.ok(null);
    }

    @RequiresPermission("sys:role:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        if (relMapper.countUsersWithRole(id) == 0) roleMapper.updateStatus(id, 3);
        return ApiResponse.ok(null);
    }

    @RequiresPermission("sys:role:assign-perm")
    @PutMapping("/{id}/permissions")
    public ApiResponse<RolePermService.RolePermResult> assignPermissions(@PathVariable long id,
            @RequestBody AssignPermsRequest req) {
        return ApiResponse.ok(rolePermService.assignPermissions(id, req.expectedVersion(), req.permissionIds(),
                RequestContext.getCurrentUser() == null ? 0 : RequestContext.getCurrentUser().userId()));
    }

    public record RoleCreateRequest(String code, String name, String description) {}
    public record AssignPermsRequest(long expectedVersion, java.util.Set<Long> permissionIds) {}
}
