package com.example.rbac;

import com.example.rbac.api.annotation.NoAudit;
import com.example.rbac.api.annotation.PublicApi;
import com.example.rbac.api.annotation.RequiresLogin;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.security.PasswordHasher;
import com.example.rbac.api.service.UserAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"RBAC_ADMIN_PASSWORD=Admin@12345"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RbacIntegrationTest {

    private static final String ADMIN_PASSWORD = "Admin@123456";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private String adminToken;

    @BeforeAll
    void setUp() throws Exception {
        // The initializer creates admin with must_change_password=1. Clear it once
        // so management tests do not have to pass through the forced-change page.
        userAuthService.changePassword(1L, passwordHasher.hash(ADMIN_PASSWORD));
        adminToken = login("admin", ADMIN_PASSWORD);
    }

    @Test
    @Order(0)
    void everyApiEndpointHasExactlyOneAuthorizationDeclaration() {
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            boolean api = info.getPatternValues().stream().anyMatch(path -> path.startsWith("/api/"));
            if (!api || info.getPatternValues().stream().anyMatch(path -> path.startsWith("/api/test/"))) {
                return;
            }
            int declarations = 0;
            if (handler.hasMethodAnnotation(PublicApi.class)
                    || handler.getBeanType().isAnnotationPresent(PublicApi.class)) {
                declarations++;
            }
            if (handler.hasMethodAnnotation(RequiresLogin.class)
                    || handler.getBeanType().isAnnotationPresent(RequiresLogin.class)) {
                declarations++;
            }
            if (handler.hasMethodAnnotation(RequiresPermission.class)) {
                declarations++;
            }
            assertThat(declarations)
                    .as("authorization declarations on %s", handler)
                    .isEqualTo(1);
            NoAudit noAudit = handler.getMethodAnnotation(NoAudit.class);
            if (noAudit != null) {
                assertThat(noAudit.value()).isNotBlank();
            }
        });
    }

    @Test
    @Order(0)
    void everyRequiredPermissionCodeExistsInSeedDataAsApiOrWildcard() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT code, resource_type FROM sys_permission WHERE status = 1");
        Map<String, String> defined = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            defined.put(String.valueOf(row.get("code")), String.valueOf(row.get("resource_type")));
        }
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            RequiresPermission required = handler.getMethodAnnotation(RequiresPermission.class);
            if (required == null) {
                return;
            }
            String code = required.value();
            assertThat(defined)
                    .as("permission code used by %s", handler)
                    .containsKey(code);
            boolean wildcard = "*".equals(code) || code.endsWith("*");
            assertThat(wildcard || "API".equals(defined.get(code)))
                    .as("@RequiresPermission must use an API or wildcard code on %s, got %s (%s)",
                            handler, code, defined.get(code))
                    .isTrue();
        });
    }

    @Test
    @Order(1)
    void loginAndMeExposePermissionsWithoutRoleChecks() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.permissions[0]").value("*"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.roles[0].code").value("SUPER_ADMIN"));
    }

    @Test
    @Order(2)
    void missingAndInvalidTokensAreRejected() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/api/users").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @Order(3)
    void lowPrivilegeUserGets403() throws Exception {
        long userId = createUser("lowuser", "Low@12345");
        String token = prepareUser("lowuser", "Low@12345", "Low@123456", userId);
        mvc.perform(get("/api/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
        mvc.perform(post("/api/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "x", "nickname", "x", "initialPassword", "Xx@123456"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void mustChangePasswordOnlyAllowsPasswordLogoutAndMe() throws Exception {
        long userId = createUser("mustuser", "Must@12345");
        String initialToken = login("mustuser", "Must@12345");
        mvc.perform(get("/api/users").header("Authorization", bearer(initialToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(initialToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        mvc.perform(put("/api/auth/password")
                        .header("Authorization", bearer(initialToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("oldPassword", "Must@12345", "newPassword", "Must@123456"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(initialToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        String newToken = login("mustuser", "Must@123456");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(newToken)))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
        assertThat(userId).isPositive();
    }

    @Test
    @Order(5)
    void overwriteRoleAssignmentRejectsStaleVersion() throws Exception {
        long userId = createUser("versionuser", "Version@12345");
        mvc.perform(put("/api/users/" + userId + "/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleIds", Set.of(2), "expectedVersion", 0))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/users/" + userId + "/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleIds", Set.of(2), "expectedVersion", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40950));
        mvc.perform(get("/api/users/" + userId + "/roles")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @Order(6)
    void inheritanceGrantsPermissionsAndPermissionChangeInvalidatesSnapshot() throws Exception {
        long base = createRole("IT_BASE", 0);
        assignPermissions(base, Set.of("sys:user:read"), 0);
        long child = createRole("IT_LEAD", base);
        long userId = createUser("inherituser", "Inherit@12345");
        assignRoles(userId, Set.of(child), 0);
        String token = prepareUser("inherituser", "Inherit@12345", "Inherit@123456", userId);

        mvc.perform(get("/api/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/users/999999").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());

        assignPermissions(base, Set.of(), 1);
        mvc.perform(get("/api/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void permitAndPermissionCheckShareTheSameDecision() throws Exception {
        long role = createRole("IT_READ", 0);
        assignPermissions(role, Set.of("sys:user:read"), 0);
        long userId = createUser("checkuser", "Check@12345");
        assignRoles(userId, Set.of(role), 0);
        String token = prepareUser("checkuser", "Check@12345", "Check@123456", userId);

        mvc.perform(post("/api/auth/permit")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("permissionCode", "sys:user:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.degraded").value(false));
        mvc.perform(post("/api/auth/permit")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("permissionCode", "sys:user:delete"))))
                .andExpect(jsonPath("$.data.allowed").value(false));

        mvc.perform(post("/api/auth/permission-check")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userId", userId,
                                "permissionCodes", List.of("sys:user:read", "sys:user:delete")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].allowed").value(true))
                .andExpect(jsonPath("$.data.results[1].allowed").value(false))
                .andExpect(jsonPath("$.data.results[1].reason").value("NO_PERMISSION"));

        mvc.perform(post("/api/auth/permission-check")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userId", 999999,
                                "permissionCodes", List.of("sys:user:read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].reason").value("USER_NOT_FOUND"));
    }

    @Test
    @Order(8)
    void auditQueryRequiresTimeRangeAndReturnsRecords() throws Exception {
        mvc.perform(get("/api/audit-logs")
                        .header("Authorization", bearer(adminToken))
                        .param("startTime", "2020-01-01T00:00:00Z")
                        .param("endTime", "2035-01-01T00:00:00Z")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray());
        mvc.perform(get("/api/audit-logs").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mvc.perform(get("/api/audit-logs")
                        .header("Authorization", bearer(adminToken))
                        .param("startTime", "2020-01-01T00:00:00Z")
                        .param("endTime", "2035-01-01T00:00:00Z")
                        .param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @Order(9)
    void logoutInvalidatesEveryTokenForTheAccount() throws Exception {
        long userId = createUser("logoutuser", "Logout@12345");
        String first = prepareUser("logoutuser", "Logout@12345", "Logout@123456", userId);
        String second = login("logoutuser", "Logout@123456");
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(first)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(second)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @Order(10)
    void endpointWithoutDeclarationIsDeniedByDefault() throws Exception {
        mvc.perform(get("/api/test/unprotected").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @Order(11)
    void builtinRolesPermissionsAndSelfAreProtected() throws Exception {
        mvc.perform(delete("/api/roles/1").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40320));
        mvc.perform(put("/api/roles/1/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("permissionCodes", Set.of(), "expectedVersion", 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40321));
        mvc.perform(delete("/api/permissions/1").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40331));
        mvc.perform(put("/api/users/1/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40310));
    }

    @Test
    @Order(12)
    void validationConflictsAndNotFoundAreDistinct() throws Exception {
        mvc.perform(post("/api/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "admin", "nickname", "重复",
                                "initialPassword", "Admin@123456"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
        mvc.perform(get("/api/users/999999").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
        mvc.perform(get("/api/users").param("size", "201").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        mvc.perform(post("/api/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("code", "BadCode", "name", "bad", "resourceType", "API"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40020));
    }

    @Test
    @Order(13)
    void inheritanceCycleIsRejected() throws Exception {
        long first = createRole("CYCLE_A", 0);
        long second = createRole("CYCLE_B", first);
        mvc.perform(put("/api/roles/" + first)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "CYCLE_A", "sort", 0, "inheritRoleId", second))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40930));
    }

    @Test
    @Order(14)
    void loginResponseCarriesUserObjectAndMultiSessionKeepsBothTokens() throws Exception {
        long userId = createUser("sessionuser", "Session@12345");
        String first = prepareUser("sessionuser", "Session@12345", "Session@123456", userId);
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "sessionuser", "password", "Session@123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("sessionuser"))
                .andReturn();
        assertThat(read(loginResult).path("data").has("userId")).isFalse();
        String second = read(loginResult).path("data").path("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(first))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(second))).andExpect(status().isOk());

        MvcResult refreshResult = mvc.perform(post("/api/auth/refresh")
                        .header("Authorization", bearer(second)))
                .andExpect(status().isOk())
                .andReturn();
        String refreshed = read(refreshResult).path("data").path("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(refreshed)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(first)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(15)
    void repeatedLoginFailuresLockTheAccount() throws Exception {
        createUser("lockuser", "Lock@12345");
        for (int attempt = 1; attempt <= 4; attempt++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("username", "lockuser", "password", "Wrong@12345"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40110));
        }
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "lockuser", "password", "Wrong@12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40112));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "lockuser", "password", "Lock@12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40112));
    }

    @Test
    @Order(16)
    void rolePermissionOverwriteRejectsStaleVersion() throws Exception {
        long role = createRole("PERM_VERSION", 0);
        assignPermissions(role, Set.of("sys:user:read"), 0);
        mvc.perform(put("/api/roles/" + role + "/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("permissionCodes", Set.of(), "expectedVersion", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40950));
    }

    @Test
    @Order(17)
    void adminCannotRemoveItsOwnLastManagementRole() throws Exception {
        MvcResult roles = mvc.perform(get("/api/users/1/roles").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        int version = read(roles).path("data").path("version").asInt();
        mvc.perform(put("/api/users/1/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleIds", Set.of(), "expectedVersion", version))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40311));
    }

    @Test
    @Order(18)
    void auditCursorEndsWithNullNextLastId() throws Exception {
        Long lastId = null;
        int pages = 0;
        boolean sawRecords = false;
        while (pages++ < 200) {
            var request = get("/api/audit-logs")
                    .header("Authorization", bearer(adminToken))
                    .param("startTime", "2020-01-01T00:00:00Z")
                    .param("endTime", "2035-01-01T00:00:00Z")
                    .param("size", "1");
            if (lastId != null) {
                request.param("lastId", Long.toString(lastId));
            }
            MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
            JsonNode page = read(result).path("data");
            if (page.path("records").size() > 0) {
                sawRecords = true;
            }
            if (!page.hasNonNull("nextLastId")) {
                break;
            }
            lastId = page.path("nextLastId").asLong();
        }
        assertThat(sawRecords).isTrue();
        assertThat(pages).isLessThan(200);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return read(result).path("data").path("accessToken").asText();
    }

    private long createUser(String username, String initialPassword) throws Exception {
        MvcResult result = mvc.perform(post("/api/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "nickname", "测试-" + username,
                                "status", 1, "initialPassword", initialPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return read(result).path("data").path("id").asLong();
    }

    private String prepareUser(String username, String initialPassword,
                               String newPassword, long userId) throws Exception {
        String initialToken = login(username, initialPassword);
        mvc.perform(put("/api/auth/password")
                        .header("Authorization", bearer(initialToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("oldPassword", initialPassword, "newPassword", newPassword))))
                .andExpect(status().isOk());
        assertThat(userId).isPositive();
        return login(username, newPassword);
    }

    private long createRole(String code, long inheritRoleId) throws Exception {
        MvcResult result = mvc.perform(post("/api/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("code", code, "name", code, "sort", 0,
                                "inheritRoleId", inheritRoleId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return read(result).path("data").path("id").asLong();
    }

    private void assignPermissions(long roleId, Set<String> codes, int expectedVersion) throws Exception {
        mvc.perform(put("/api/roles/" + roleId + "/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("permissionCodes", codes, "expectedVersion", expectedVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void assignRoles(long userId, Set<Long> roleIds, int expectedVersion) throws Exception {
        mvc.perform(put("/api/users/" + userId + "/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("roleIds", roleIds, "expectedVersion", expectedVersion))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
