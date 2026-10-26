# RBAC 权限系统

一个基于 Java 21、Spring Boot 3、MyBatis 与 Vue 3 的模块化单体 RBAC 权限系统。当前实现覆盖文档定义的 **RBAC0**：认证、用户/角色/权限管理、接口/菜单/按钮三类权限、权限快照与失效通知、CHANGE/SECURITY 审计、管理端前端，并预留并实现了 RBAC1 角色继承。

## 技术栈

- 后端：Java 21、Spring Boot 3.4、Maven
- 数据访问：MyBatis、Flyway；生产 MySQL 8，本地/测试可用 H2（MySQL 模式）
- 缓存：Caffeine + `AtomicReference`；Redis Pub/Sub 用于跨实例失效通知（可关闭）
- 认证：自研 JWT 过滤器（HS256 + kid），密码仅使用 `spring-security-crypto` 的 BCrypt
- 前端：Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios
- 观测：Actuator、Micrometer、Prometheus、traceId

## 目录结构

```text
.
├── rbac-api/          契约层：注解、DTO、应用服务接口、快照模型、错误码、常量
├── rbac-core/         领域与持久化：Mapper、应用服务实现、权限快照源、约束校验链
├── rbac-auth/         登录、JWT、密码策略、限流、/api/auth 接口
├── rbac-guard/        JWT 过滤器、权限拦截器、默认拒绝
├── rbac-cache/        权限快照、身份快照、单飞重载、Redis 失效广播
├── rbac-audit/        CHANGE/SECURITY 审计写入与游标查询
├── rbac-admin/        用户、角色、权限、审计管理端 REST 接口
├── rbac-bootstrap/    Spring Boot 启动、配置、异常处理、Flyway、初始 admin
├── web/               Vue 3 管理端
├── docs/              需求、设计、数据模型、接口与演进文档
├── example/           业务侧示例数据（未自动导入）
├── Dockerfile         后端镜像构建文件
├── compose.yml        本地 MySQL、Redis 和应用编排
└── pom.xml            Maven 多模块聚合工程
```

## 环境要求

- JDK 21（`maven.compiler.release=21`）
- Maven 3.9+
- Node.js 20+、npm 10+
- Docker Desktop（仅容器运行 MySQL/Redis 时需要）

## 快速开始

### 1. 后端：本地 H2（无需 MySQL/Redis）

本地 `local` profile 使用内存 H2（MySQL 模式）、Flyway 自动建表，并关闭 Redis。启动即建表、创建初始 admin；**重启会重置本地数据**，需要持久化时使用 Compose/MySQL。本地每次启动都需要设置初始 admin 密码与 JWT 密钥（仓库不提交任何可用密钥）：

```bash
# Linux/macOS
export RBAC_ADMIN_PASSWORD='Admin@12345'
export RBAC_JWT_KEY_1='<至少 32 字节的随机字符串>'

# Windows PowerShell
$env:RBAC_ADMIN_PASSWORD='Admin@12345'
$env:RBAC_JWT_KEY_1='<至少 32 字节的随机字符串>'

mvn -B test
mvn -B package
java -jar rbac-bootstrap/target/rbac-bootstrap-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

启动后：

- 服务：`http://localhost:8080`
- 健康检查：`http://localhost:8080/actuator/health`
- 初始账号：`admin`
- 初始密码：环境变量 `RBAC_ADMIN_PASSWORD`
- JWT 密钥：环境变量 `RBAC_JWT_KEY_1`（至少 32 字节）
- 首次登录会被强制修改密码；`must_change_password` 改为 0 后才能访问业务接口。

`local` 每次启动都会重新执行 Flyway 并重建 `admin`，因此每次启动都需要 `RBAC_ADMIN_PASSWORD` 与 `RBAC_JWT_KEY_1`。生产/容器 profile 仍使用 MySQL 8，同一套 Flyway 脚本可在 MySQL 与 H2(MySQL 模式) 上执行。

### 2. 前端

```bash
cd web
npm ci
npm run dev
```

开发地址 `http://localhost:5173`，`/api` 由 Vite 代理到 `http://localhost:8080`。构建与类型检查：

```bash
npm run type-check
npm run build
```

### 3. Docker Compose（MySQL + Redis + 应用）

先打包后端 JAR，并提供初始密码与 JWT 密钥（密钥至少 32 字节，不能提交到仓库）：

```bash
mvn -B package
export RBAC_ADMIN_PASSWORD='<至少 8 位且含大小写与数字>'
export RBAC_JWT_KEY_1='<至少 32 字节的随机字符串>'
docker compose up --build
```

默认服务：

| 服务 | 地址 | 用途 |
|---|---|---|
| `rbac-app` | `http://localhost:8080` | 后端应用 |
| `mysql` | `localhost:3306` | 开发数据库 |
| `redis` | `localhost:6379` | 失效通知与登录限流 |

Compose 中的数据库密码仅用于本地开发。生产配置、JWT 密钥和数据库密码不得提交到仓库。

## 配置与密钥

| 变量 | 说明 | 必填 |
|---|---|---|
| `RBAC_ADMIN_PASSWORD` | 首次启动创建 `admin` 的初始密码 | 首次启动 |
| `RBAC_JWT_KEY_1` | 当前 HS256 密钥，≥ 32 字节 | local / container |
| `RBAC_TRUSTED_PROXIES` | 可信代理地址/CIDR，逗号分隔；为空表示不信任 XFF | 否 |
| `RBAC_CORS_ORIGINS` | 允许的前端 Origin，逗号分隔，精确匹配 | 否 |
| `MYSQL_HOST` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` | 容器数据库连接 | container |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接 | container |

关键配置项：

- `rbac.cache.soft-ttl-seconds`：权限/身份快照软 TTL，默认 30 秒；到期只触发重载，不丢弃数据。
- `rbac.cache.stale-policy`：`fail-open`（默认）或 `fail-closed`。
- `rbac.redis.enabled`：关闭时退化为单实例本地失效 + 软 TTL。
- `rbac.jwt.keys` / `rbac.jwt.active-kid`：kid → 密钥映射，支持轮换期双密钥。
- `rbac.client-ip.trusted-proxies`：只信任这些代理写入的 `X-Forwarded-For`。

## 接口概览

基础路径 `/api`，统一响应 `{ code, message, data, traceId }`。HTTP 状态由业务码前缀映射（400xx→400、401xx→401、403xx→403、409xx→409、429xx→429、5xxxx→5xx）。

| 分组 | 接口 |
|---|---|
| 认证 | `POST /api/auth/login`、`GET /me`、`POST /logout`、`POST /refresh`、`PUT /password`、`POST /permit`、`POST /permission-check` |
| 用户 | `GET/POST /api/users`、`GET/PUT/DELETE /api/users/{id}`、`PUT /{id}/status`、`PUT /{id}/password:reset`、`GET/PUT /{id}/roles` |
| 角色 | `GET/POST /api/roles`、`GET /options`、`GET /tree`、`GET/PUT/DELETE /{id}`、`PUT /{id}/status`、`GET/PUT /{id}/permissions` |
| 权限 | `GET /api/permissions/tree`、`GET/POST/PUT/DELETE /{id}` |
| 审计 | `GET /api/audit-logs`（必填时间范围，游标分页） |

完整契约见 [docs/04-接口设计.md](./docs/04-接口设计.md)。`@PublicApi`、`@RequiresLogin`、`@RequiresPermission` 三者都不标注的 Controller 方法会被默认拒绝并告警。

## 验证与质量门禁

```bash
mvn -B test        # 单元 + H2 集成 + ArchUnit + 接口声明/权限码一致性门禁
mvn -B package
cd web && npm run type-check && npm run build
```

> 受限沙箱若无法写入默认 `~/.m2`，可把 Maven 本地仓库指向工作区：
> `mvn -s .mvn-local-settings.xml -Dmaven.repo.local=.m2repo ...`。
> `.m2repo/` 与 `.mvn-local-settings.xml` 已加入 `.gitignore`，不属于交付内容。

当前测试覆盖：

- 权限匹配、权限码格式、密码策略、JWT 签发/验签/轮换/kid 失效、客户端 IP 解析。
- 权限快照：无角色、单角色、多角色、停用角色、继承链、停用中断、环降级、5/6 层深度、6 个直接角色、失效单飞重载、fail-open/fail-closed。
- 集成：登录/多会话、强制改密、401/403/404/409 语义、默认拒绝、越权、覆盖式授权 40950、角色继承授权与权限变更即时失效、permit/permission-check 一致性、登出全令牌撤销、审计游标、内置角色/权限保护。
- 尚未自动化覆盖：429 限流（test profile 关闭 Redis）、初始 admin 首次登录/补绑、AC-11 真并发、AC-7 审计异常注入、AC-18/19/20/21。
- ArchUnit：R-1/R-2/R-3/R-5/R-6/R-8；R-4/R-7 在无 `biz-*` 模块时不适用，R-9 由契约单实现约束。

## 当前状态

- [x] Maven 多模块工程与严格依赖方向
- [x] Flyway RBAC0 表、索引、内置角色/权限数据、回滚脚本
- [x] `rbac-api` 契约与应用服务接口
- [x] 登录、JWT、密码策略、失败锁定、登录限流、强制改密、多会话、登出/续期
- [x] RBAC0 权限计算 + RBAC1 继承链（含环与深度保护）
- [x] `PermissionSnapshot` / `IdentitySnapshot`、异步单飞重载、30 秒软 TTL、Redis 广播与降级指标
- [x] 用户/角色/权限/审计管理端接口与 Vue 3 管理端
- [x] CHANGE 同事务审计、SECURITY 独立审计、游标查询
- [x] 单元测试、H2 集成测试、ArchUnit 与接口门禁
- [ ] 真实 MySQL 目标数据量下的 `EXPLAIN ANALYZE` 与 2,500/4,000 QPS 压测（当前环境无 MySQL/压测设施）
- [ ] Redis 断连、跨实例广播、MySQL 故障与密钥轮换的故障演练（需多实例/中间件环境）
- [ ] CI/CD 配置
- [ ] 示例数据中的 10,000 员工与 18 个外部系统 359 个功能点未导入（按 02 §12.1 留待上线前由管理员录入）

## 文档入口

1. [编码代理工作基线](./docs/00-编码代理工作基线.md)
2. [需求规格说明书](./docs/01-需求规格说明书.md)
3. [总体设计说明书](./docs/02-总体设计说明书.md)
4. [数据模型设计](./docs/03-数据模型设计.md)
5. [接口设计](./docs/04-接口设计.md)
6. [RBAC0 到 RBAC3 演进设计](./docs/05-RBAC0到RBAC3演进设计.md)
7. [修订记录](./docs/06-修订记录.md)

完整索引见 [docs/README.md](./docs/README.md)。

## 协作规则

- 不直接向 `main` 和 `develop` 推送；
- 一个任务使用一个 `feature` 或 `fix` 分支；
- 合并前必须通过自动检查并由队友评审；
- 生产环境只发布已经批准的固定版本；
- 详细流程由团队按仓库约定维护。
