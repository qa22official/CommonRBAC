package com.example.rbac.config;

import com.example.rbac.api.auth.AuthAccount;
import com.example.rbac.api.dto.RoleSummary;
import com.example.rbac.api.dto.user.AssignRolesCommand;
import com.example.rbac.api.dto.user.CreateUserCommand;
import com.example.rbac.api.dto.user.UserRoles;
import com.example.rbac.api.security.PasswordHasher;
import com.example.rbac.api.service.RoleAdminService;
import com.example.rbac.api.service.UserAdminService;
import com.example.rbac.api.service.UserAuthService;
import com.example.rbac.api.security.PasswordPolicy;
import com.example.rbac.api.support.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Creates the initial admin from {@code RBAC_ADMIN_PASSWORD} when absent. The
 * password never enters Flyway, the image or the repository; a missing variable
 * on first start fails startup (ADR-24). If the account already exists the
 * initializer only repairs a missing SUPER_ADMIN binding and never resets the
 * password.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class InitialAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);
    private static final String PASSWORD_ENV = "RBAC_ADMIN_PASSWORD";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AdminBootstrapProperties properties;
    private final UserAuthService userAuthService;
    private final UserAdminService userAdminService;
    private final RoleAdminService roleAdminService;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final Environment environment;

    public InitialAdminInitializer(AdminBootstrapProperties properties,
                                   UserAuthService userAuthService,
                                   UserAdminService userAdminService,
                                   RoleAdminService roleAdminService,
                                   PasswordHasher passwordHasher,
                                   PasswordPolicy passwordPolicy,
                                   Environment environment) {
        this.properties = properties;
        this.userAuthService = userAuthService;
        this.userAdminService = userAdminService;
        this.roleAdminService = roleAdminService;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        long superRoleId = superAdminRoleId();
        Optional<AuthAccount> existing = userAuthService.findForLogin(properties.getUsername());
        if (existing.isPresent()) {
            ensureSuperAdmin(existing.get().id(), superRoleId);
            return;
        }
        String password = environment.getProperty(PASSWORD_ENV);
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "首次启动必须设置环境变量 " + PASSWORD_ENV + "（初始 admin 密码）");
        }
        Optional<String> violation = passwordPolicy.violation(properties.getUsername(), password);
        if (violation.isPresent()) {
            throw new IllegalStateException(
                    "环境变量 " + PASSWORD_ENV + " 不符合密码策略：" + violation.get());
        }
        long userId = userAdminService.create(new CreateUserCommand(
                0L,
                properties.getUsername(),
                properties.getNickname(),
                null,
                null,
                1,
                "系统初始管理员",
                passwordHasher.hash(password)));
        assignSuperAdmin(userId, superRoleId);
        log.warn("Initial admin '{}' created; the first login must change the password",
                properties.getUsername());
    }

    private long superAdminRoleId() {
        return roleAdminService.options().stream()
                .filter(role -> SUPER_ADMIN.equals(role.code()))
                .map(RoleSummary::id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("内置角色 SUPER_ADMIN 不存在，请检查 Flyway 初始化数据"));
    }

    private void ensureSuperAdmin(long userId, long superRoleId) {
        UserRoles roles = userAdminService.findRoles(userId)
                .orElseThrow(() -> new IllegalStateException("初始管理员账号状态异常"));
        if (roles.roleIds().contains(superRoleId)) {
            return;
        }
        // "补绑" means append, not replace: keep any roles the initial admin
        // already holds (06 R-10).
        Set<Long> finalRoleIds = new LinkedHashSet<>(roles.roleIds());
        finalRoleIds.add(superRoleId);
        assignRoles(userId, finalRoleIds, roles.version());
        log.warn("Initial admin '{}' existed without SUPER_ADMIN; binding restored without removing other roles",
                properties.getUsername());
    }

    private void assignSuperAdmin(long userId, long superRoleId) {
        try {
            UserRoles roles = userAdminService.findRoles(userId).orElseThrow();
            assignRoles(userId, Set.of(superRoleId), roles.version());
        } catch (BusinessException ex) {
            throw new IllegalStateException("无法为初始管理员绑定 SUPER_ADMIN: " + ex.getMessage(), ex);
        }
    }

    private void assignRoles(long userId, Set<Long> roleIds, int expectedVersion) {
        try {
            userAdminService.assignRoles(new AssignRolesCommand(0L, userId, roleIds, expectedVersion));
        } catch (BusinessException ex) {
            throw new IllegalStateException("无法为初始管理员绑定 SUPER_ADMIN: " + ex.getMessage(), ex);
        }
    }
}
