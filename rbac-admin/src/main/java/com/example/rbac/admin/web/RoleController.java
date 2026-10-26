package com.example.rbac.admin.web;

import com.example.rbac.api.annotation.NoAudit;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.constant.Permissions;
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
import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.service.RoleAdminService;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleAdminService roleAdminService;

    public RoleController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping
    @RequiresPermission(Permissions.SYS_ROLE_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<PageResult<RoleListItem>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(roleAdminService.list(new RoleQuery(page, size, keyword, status)),
                ControllerSupport.traceId());
    }

    @GetMapping("/options")
    @RequiresPermission(Permissions.SYS_ROLE_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<List<RoleSummary>> options() {
        return ApiResponse.success(roleAdminService.options(), ControllerSupport.traceId());
    }

    @GetMapping("/tree")
    @RequiresPermission(Permissions.SYS_ROLE_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<List<RoleTreeNode>> tree() {
        return ApiResponse.success(roleAdminService.tree(), ControllerSupport.traceId());
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permissions.SYS_ROLE_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<RoleDetail> detail(@PathVariable long id) {
        return ApiResponse.success(roleAdminService.findDetail(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "角色不存在")),
                ControllerSupport.traceId());
    }

    @PostMapping
    @RequiresPermission(Permissions.SYS_ROLE_CREATE)
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody CreateRoleRequest body,
                                                 HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        long id = roleAdminService.create(new CreateRoleCommand(current.userId(), body.code(),
                body.name(), body.description(), body.sort(), body.inheritRoleId()));
        return ApiResponse.success(Map.of("id", id), ControllerSupport.traceId());
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permissions.SYS_ROLE_UPDATE)
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody UpdateRoleRequest body,
                                    HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        roleAdminService.update(new UpdateRoleCommand(current.userId(), id, body.name(),
                body.description(), body.sort(), body.inheritRoleId()));
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permissions.SYS_ROLE_DELETE)
    public ApiResponse<Void> delete(@PathVariable long id, HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        roleAdminService.delete(current.userId(), id);
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    @PutMapping("/{id}/status")
    @RequiresPermission(Permissions.SYS_ROLE_TOGGLE)
    public ApiResponse<Void> changeStatus(@PathVariable long id,
                                          @Valid @RequestBody ChangeStatusRequest body,
                                          HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        roleAdminService.changeStatus(new ChangeRoleStatusCommand(current.userId(), id, body.status()));
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    @GetMapping("/{id}/permissions")
    @RequiresPermission(Permissions.SYS_ROLE_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<RolePermissions> permissions(@PathVariable long id) {
        return ApiResponse.success(roleAdminService.findPermissions(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "角色不存在")),
                ControllerSupport.traceId());
    }

    @PutMapping("/{id}/permissions")
    @RequiresPermission(Permissions.SYS_ROLE_ASSIGN_PERM)
    public ApiResponse<Void> assignPermissions(@PathVariable long id,
                                               @Valid @RequestBody AssignPermissionsRequest body,
                                               HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        roleAdminService.assignPermissions(new AssignPermissionsCommand(current.userId(), id,
                body.permissionCodes(), body.expectedVersion()));
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    public record CreateRoleRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 64) String name,
            @Size(max = 255) String description,
            @PositiveOrZero Integer sort,
            @PositiveOrZero Long inheritRoleId) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 255) String description,
            @PositiveOrZero Integer sort,
            @PositiveOrZero Long inheritRoleId) {
    }

    public record ChangeStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {
    }

    public record AssignPermissionsRequest(@NotNull Set<String> permissionCodes,
                                           @NotNull @Min(0) Integer expectedVersion) {
    }
}
