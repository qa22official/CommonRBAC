package com.example.rbac.admin.web;

import com.example.rbac.api.annotation.NoAudit;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.constant.Permissions;
import com.example.rbac.api.dto.PageResult;
import com.example.rbac.api.dto.user.AssignRolesCommand;
import com.example.rbac.api.dto.user.ChangeUserStatusCommand;
import com.example.rbac.api.dto.user.CreateUserCommand;
import com.example.rbac.api.dto.user.ResetPasswordCommand;
import com.example.rbac.api.dto.user.UpdateUserCommand;
import com.example.rbac.api.dto.user.UserListItem;
import com.example.rbac.api.dto.user.UserProfile;
import com.example.rbac.api.dto.user.UserQuery;
import com.example.rbac.api.dto.user.UserRoles;
import com.example.rbac.api.identity.CurrentUser;
import com.example.rbac.api.security.PasswordHasher;
import com.example.rbac.api.security.PasswordPolicy;
import com.example.rbac.api.service.UserAdminService;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserAdminService userAdminService;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;

    public UserController(UserAdminService userAdminService,
                          PasswordHasher passwordHasher,
                          PasswordPolicy passwordPolicy) {
        this.userAdminService = userAdminService;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
    }

    @GetMapping
    @RequiresPermission(Permissions.SYS_USER_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<PageResult<UserListItem>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(userAdminService.list(new UserQuery(page, size, keyword, status)),
                ControllerSupport.traceId());
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permissions.SYS_USER_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<UserProfile> detail(@PathVariable long id) {
        return ApiResponse.success(userAdminService.findProfile(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在")),
                ControllerSupport.traceId());
    }

    @PostMapping
    @RequiresPermission(Permissions.SYS_USER_CREATE)
    public ApiResponse<Map<String, Long>> create(@Valid @RequestBody CreateUserRequest body,
                                                 HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        var violation = passwordPolicy.violation(body.username(), body.initialPassword());
        if (violation.isPresent()) {
            throw new BusinessException(ErrorCodes.PASSWORD_WEAK, violation.get());
        }
        long id = userAdminService.create(new CreateUserCommand(
                current.userId(),
                body.username(),
                body.nickname(),
                body.email(),
                body.phone(),
                body.status(),
                body.remark(),
                passwordHasher.hash(body.initialPassword())));
        return ApiResponse.success(Map.of("id", id), ControllerSupport.traceId());
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permissions.SYS_USER_UPDATE)
    public ApiResponse<Void> update(@PathVariable long id,
                                    @Valid @RequestBody UpdateUserRequest body,
                                    HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        userAdminService.update(new UpdateUserCommand(current.userId(), id,
                body.nickname(), body.email(), body.phone(), body.remark()));
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(Permissions.SYS_USER_DELETE)
    public ApiResponse<Void> delete(@PathVariable long id, HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        userAdminService.delete(current.userId(), id);
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    @PutMapping("/{id}/status")
    @RequiresPermission(Permissions.SYS_USER_TOGGLE)
    public ApiResponse<Void> changeStatus(@PathVariable long id,
                                          @Valid @RequestBody ChangeStatusRequest body,
                                          HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        userAdminService.changeStatus(new ChangeUserStatusCommand(current.userId(), id, body.status()));
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    @PutMapping("/{id}/password:reset")
    @RequiresPermission(Permissions.SYS_USER_RESET_PASSWORD)
    public ApiResponse<Void> resetPassword(@PathVariable long id,
                                           @Valid @RequestBody ResetPasswordRequest body,
                                           HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        UserProfile profile = userAdminService.findProfile(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在"));
        var violation = passwordPolicy.violation(profile.username(), body.newPassword());
        if (violation.isPresent()) {
            throw new BusinessException(ErrorCodes.PASSWORD_WEAK, violation.get());
        }
        userAdminService.resetPassword(new ResetPasswordCommand(current.userId(), id,
                passwordHasher.hash(body.newPassword())));
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    @GetMapping("/{id}/roles")
    @RequiresPermission(Permissions.SYS_USER_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<UserRoles> roles(@PathVariable long id) {
        return ApiResponse.success(userAdminService.findRoles(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "用户不存在")),
                ControllerSupport.traceId());
    }

    @PutMapping("/{id}/roles")
    @RequiresPermission(Permissions.SYS_USER_ASSIGN_ROLE)
    public ApiResponse<Void> assignRoles(@PathVariable long id,
                                         @Valid @RequestBody AssignRolesRequest body,
                                         HttpServletRequest request) {
        CurrentUser current = ControllerSupport.currentUser(request);
        userAdminService.assignRoles(new AssignRolesCommand(current.userId(), id,
                body.roleIds(), body.expectedVersion()));
        return ApiResponse.success(null, ControllerSupport.traceId());
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 64) String nickname,
            @Email @Size(max = 128) String email,
            @Size(max = 32) String phone,
            @Min(0) @Max(1) Integer status,
            @Size(max = 255) String remark,
            @NotBlank @Size(max = 72) String initialPassword) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 64) String nickname,
            @Email @Size(max = 128) String email,
            @Size(max = 32) String phone,
            @Size(max = 255) String remark) {
    }

    public record ChangeStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 72) String newPassword) {
    }

    public record AssignRolesRequest(@NotNull Set<Long> roleIds,
                                     @NotNull @Min(0) Integer expectedVersion) {
    }
}
