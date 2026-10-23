package com.example.rbac.admin.api;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.rbac.admin.internal.UserRoleService;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.audit.AuditRecorder;
import com.example.rbac.api.permission.FrameworkPermissionCodes;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.RequestContext;
import com.example.rbac.core.internal.mapper.RelMapper;
import com.example.rbac.core.internal.mapper.UserMapper;
import com.example.rbac.auth.internal.PasswordPolicy;

/** 用户管理 CRUD。覆盖式授权走 UserRoleService（自带 FOR UPDATE + 版本 + 约束 + 广播）。 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserMapper userMapper;
    private final RelMapper relMapper;
    private final AuditRecorder audit;
    private final UserRoleService userRoleService;

    public UserController(UserMapper userMapper, RelMapper relMapper, AuditRecorder audit, UserRoleService userRoleService) {
        this.userMapper = userMapper;
        this.relMapper = relMapper;
        this.audit = audit;
        this.userRoleService = userRoleService;
    }

    @RequiresPermission("sys:user:read")
    @GetMapping
    public ApiResponse<List<com.example.rbac.core.internal.domain.User>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userMapper.list(keyword, status, page * size, size));
    }

    @RequiresPermission("sys:user:create")
    @PostMapping
    public ApiResponse<com.example.rbac.core.internal.domain.User> create(@RequestBody CreateUserRequest req) {
        String reason = PasswordPolicy.validate(req.username(), req.password());
        if (reason != null) return ApiResponse.fail(400, reason);
        com.example.rbac.core.internal.domain.User user = new com.example.rbac.core.internal.domain.User(
                0L, req.username(), req.nickname(), PasswordPolicy.hash(req.password()),
                0, 1, 0, false, null, null);
        userMapper.insert(user);
        audit.record(new AuditRecord(AuditCategory.CHANGE, "CREATE_USER", null,
                RequestContext.getCurrentUser() == null ? 0 : RequestContext.getCurrentUser().userId(),
                null, "USER", req.username(), null, "{\"password\": \"***\"}", AuditRecord.RESULT_SUCCESS, null, null, null, null, null));
        return ApiResponse.ok(user);
    }

    @RequiresPermission("sys:user:update")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody UpdateUserRequest req) {
        var opt = userMapper.findById(id);
        if (opt.isEmpty()) return ApiResponse.fail(404, "user not found");
        var u = opt.get();
        userMapper.update(new com.example.rbac.core.internal.domain.User(
                u.id(), u.username(), req.nickname(), u.passwordHash(),
                u.tokenVersion(), u.status(), u.roleVersion(), u.builtin(), u.createdAt(), u.updatedAt()));
        return ApiResponse.ok(null);
    }

    @RequiresPermission("sys:user:update")
    @PutMapping("/{id}/status/{status}")
    public ApiResponse<Void> updateStatus(@PathVariable long id, @PathVariable int status) {
        userMapper.updateStatus(id, status);
        return ApiResponse.ok(null);
    }

    @RequiresPermission("sys:user:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        if (relMapper.countUsersWithRole(id) > 0) {
            userMapper.updateStatus(id, 3);
        }
        return ApiResponse.ok(null);
    }

    @RequiresPermission("sys:user:reset-password")
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable long id, @RequestBody ResetPasswordRequest req) {
        String reason = PasswordPolicy.validate(null, req.newPassword());
        if (reason != null) return ApiResponse.fail(400, reason);
        userMapper.updatePasswordAndBump(id, PasswordPolicy.hash(req.newPassword()));
        return ApiResponse.ok(null);
    }

    @RequiresPermission("sys:user:assign-role")
    @PutMapping("/{id}/roles")
    public ApiResponse<UserRoleService.UserRoleResult> assignRoles(@PathVariable long id,
            @RequestBody AssignRolesRequest req) {
        return ApiResponse.ok(userRoleService.assignRoles(id, req.expectedVersion(), req.roleIds(),
                RequestContext.getCurrentUser() == null ? 0 : RequestContext.getCurrentUser().userId()));
    }

    public record CreateUserRequest(String username, String nickname, String password) {}
    public record UpdateUserRequest(String nickname) {}
    public record ResetPasswordRequest(String newPassword) {}
    public record AssignRolesRequest(long expectedVersion, java.util.Set<Long> roleIds) {}
}
