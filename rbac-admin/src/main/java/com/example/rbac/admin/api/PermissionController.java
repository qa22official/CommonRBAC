package com.example.rbac.admin.api;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.core.internal.domain.Permission;
import com.example.rbac.core.internal.mapper.PermissionMapper;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionMapper permMapper;

    public PermissionController(PermissionMapper permMapper) {
        this.permMapper = permMapper;
    }

    @RequiresPermission("sys:perm:read")
    @GetMapping
    public ApiResponse<List<Permission>> tree() {
        return ApiResponse.ok(permMapper.tree());
    }

    @RequiresPermission("sys:perm:create")
    @PostMapping
    public ApiResponse<Permission> create(@RequestBody CreatePermRequest req) {
        var opt = com.example.rbac.api.audit.PermissionCategory.valueOf(req.category().toUpperCase());
        var p = new Permission(0L, req.code(), req.name(), req.parentId(), opt, 1, req.sort(), null, null);
        permMapper.insert(p);
        return ApiResponse.ok(p);
    }

    @RequiresPermission("sys:perm:update")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody CreatePermRequest req) {
        var opt = permMapper.findById(id);
        if (opt.isEmpty()) return ApiResponse.fail(404, "permission not found");
        permMapper.update(new Permission(opt.get().id(), opt.get().code(), req.name(), opt.get().parentId(),
                opt.get().category(), opt.get().status(), req.sort(), opt.get().createdAt(), opt.get().updatedAt()));
        return ApiResponse.ok(null);
    }

    @RequiresPermission("sys:perm:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        permMapper.updateStatus(id, 3);
        return ApiResponse.ok(null);
    }

    public record CreatePermRequest(String code, String name, long parentId, String category, int sort) {}
}
