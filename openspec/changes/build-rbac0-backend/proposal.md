# Proposal

## Why

本仓库的 CommonRBAC 后端目前仅完成了多模块 Maven 骨架和 `rbac-api` 契约层（注解、接口、事件、DTO），核心实现均为空壳。课程要求"能跑起来、部署到服务器、通过测试、API 能调通并拿到数据"。当前只有设计文档（00 基线 ~ 06 性能），还没有把这些设计约束组织成可执行的、可追溯到 spec 的实现步骤。本变更把 RBAC0 后端的完整实现收敛为一组 capability specs，让后续 `/opsx-apply` 阶段按 spec 驱动构建。

## What Changes

- **新增全部后端实现**：认证（JWT + BCrypt + 限流/锁定）、鉴权（过滤器 + 拦截器 + PermissionResolver + 默认拒绝）、缓存（本地三份映射 + Redis Pub/Sub + TTL 兜底 + fail-open）、管理端 CRUD、审计（CHANGE 同事务 / SECURITY 独立落库）、Flyway 迁移脚本与初始化数据。
- **补齐 rbac-api 契约**：`@NoAudit` 注解、`AuditRecord` 扩展导出结构、框架级权限码常量（`*`、`sys:*`）。
- **实现质量门禁**：ArchUnit 模块依赖校验、权限注解覆盖检查、权限码定义一致性检查、审计声明检查、越权集成测试。
- **新增 OpenSpec capability specs**：把 docs/ 里的设计约束（00 基线、01 需求、02~06 设计文档）按行为边界拆成 6 个 capability 的 delta spec 文件，作为 apply 阶段的输入。
- 部署相关的 Dockerfile / compose.yml 已存在，本变更**不**改它们（除非 apply 时发现运行期要求有出入，届时再单独变更）。
- **不含**：RBAC1/2/3 的继承与约束实现（仅保留 `inherit_role_id` 字段语义与 `ConstraintValidator` 校验链扩展位）、数据权限、多租户、SSO/OAuth、DSD、前端 v-perm/菜单（前端实现由单独变更覆盖）。

## Capabilities

### New Capabilities

以下 capability 全部为新建，因为 `openspec/specs/` 目录目前为空：

- `auth`：认证能力。账号密码登录（BCrypt + 限流 + 失败锁定）、JWT 签发与验签（HS256 + kid 轮换）、登出、续期、修改密码；`token_version` 令牌作废通道；FR-A 系列需求的全部实现。
- `authorization`：鉴权能力。固定顺序鉴权链路（过滤器 → 拦截器 → PermissionResolver → 权限码匹配）、默认拒绝、`@PublicApi` / `@RequiresLogin` / `@RequiresPermission` 三注解判定、通配权限码展开、内置保护规则（SUPER_ADMIN 不可删除/停用等 4 条）。
- `identity-management`：身份管理能力。用户/角色/权限/授权关系的增删改查启停；覆盖式授权的 `expectedVersion` 乐观锁 + 行锁；Flyway 五张表 + 初始化数据（SUPER_ADMIN + `*`、内置角色、`sys:*` 框架权限码）。
- `audit`：审计能力。CHANGE 类同事务落库、SECURITY 类独立落库、导出操作的 scope/resultCount/condition 固定结构、审计查询游标分页、审计只读。
- `cache`：缓存能力。三份本地映射（roleId→codes、userId→roleIds、userId→{tokenVersion,status}）、Redis Pub/Sub 失效广播（PERM_CHANGED / TOKEN_REVOKED / TOKEN_REVOKED_GLOBAL）、30 秒 TTL 兜底、fail-open 姿态与 4 类必需指标、单飞惰性重载。
- `quality-gates`：质量门禁能力。ArchUnit 依赖规则、权限注解覆盖、权限码一致性、审计声明检查、权限计算单测路径覆盖、越权集成测试、关键 SQL 索引 `EXPLAIN` 断言。

### Modified Capabilities

无（`openspec/specs/` 为空，没有既有 capability 需要改 spec 级行为）。

## Impact

| 受影响对象 | 说明 |
|---|---|
| 模块 | 除 rbac-api 外的 6 个模块（core / auth / guard / cache / audit / admin）从 package-info 起步；bootstrap 模块增加装配与配置 |
| 数据库 | 新增 5 张 RBAC 表 + 1 张审计表（逻辑删除、Flyway 管理、单一序列 V20x 起） |
| 缓存 | 本地 Guava/Caffeine + Redis Pub/Sub；Redis 不可用时降级为 TTL |
| HTTP | 基础路径 `/api`；JSON、驼峰；错误码 `code=0` 成功 / 401 / 40101 / 403 / 40950 / 503 |
| JWT | HS256；密钥 ≥ 256 位，环境变量下发；三阶段轮换协议 |
| 配置 | `application.yml` 新增 auth / cache / audit 等段；禁用 Spring Security（鉴权自建） |
| 依赖 | pom 新增 Spring Boot Web / MyBatis / JWT / Guava / Redis / BCrypt / Flyway / ArchUnit / Actuator |
| 运维 | Prometheus 指标（缓存陈旧时长、重载失败、放行请求数、鉴权耗时）；结构化 JSON 日志 |
| 不变 | 前端 v-perm 指令、RBAC1/2/3 的 `inherit_role_id` 字段只做零值占位、RBAC2 `ConstraintValidator` 链只保留 4 条内置校验器 |
