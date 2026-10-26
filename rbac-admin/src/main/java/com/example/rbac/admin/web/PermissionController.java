package com.example.rbac.admin.web;

import com.example.rbac.api.annotation.NoAudit;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.constant.Permissions;
import com.example.rbac.api.dto.permission.CreatePermissionCommand;
import com.example.rbac.api.dto.permission.PermissionDetail;
import com.example.rbac.api.dto.permission.PermissionTreeNode;
import com.example.rbac.api.dto.permission.UpdatePermissionCommand;
import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.service.PermissionAdminService;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionAdminService permissionAdminService;

    public PermissionController(PermissionAdminService permissionAdminService) {
        this.permissionAdminService = permissionAdminService;
    }

    @GetMapping("/tree")
    @RequiresPermission(Permissions.SYS_PERM_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<List<PermissionTreeNode>> tree() {
        return ApiResponse.success(permissionAdminService.tree(), ControllerSupport.traceId());
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permissions.SYS_PERM_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<PermissionDetail> detail(@PathVariable long id) {
        return ApiResponse.success(permissionAdminService.findDetail(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "权限不存在")),
                ControllerSupport.traceId());
    }

    @PostMapping
    @RequiresPermission(Permissions.SYS_PERM_CREATE)
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody CreatePermissionRequest body,
                                                 HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        long id = permissionAdminService.create(new CreatePermissionCommand(
                current.userId(), body.code(), body.name(), body.resourceType(), body.parentId(),
                body.path(), body.httpMethod(), body.icon(), body.sort()));
        return ApiResponse.success(Map.of("id", id), ControllerSupport.traceId());
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permissions.SYS_PERM_UPDATE)
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody UpdatePermissionRequest body,
                                    HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        permissionAdminService.update(new UpdatePermissionCommand(
                current.userId(), id, body.name(), body.resourceType(), body.parentId(),
                body.path(), body.httpMethod(), body.icon(), body.sort(), body.status(), body.code()));
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permissions.SYS_PERM_DELETE)
    public ApiResponse<Void> delete(@PathVariable long id, HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        permissionAdminService.delete(current.userId(), id);
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    public record CreatePermissionRequest(
            @NotBlank @Size(max = 128) String code,
            @NotBlank @Size(max = 64) String name,
            @NotBlank String resourceType,
            @PositiveOrZero Long parentId,
            @Size(max = 255) String path,
            @Size(max = 16) String httpMethod,
            @Size(max = 64) String icon,
            @PositiveOrZero Integer sort) {
    }

    public record UpdatePermissionRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank String resourceType,
            @PositiveOrZero Long parentId,
            @Size(max = 255) String path,
            @Size(max = 16) String httpMethod,
            @Size(max = 64) String icon,
            @PositiveOrZero Integer sort,
            @Min(0) @Max(1) Integer status,
            @Size(max = 128) String code) {
    }
}
