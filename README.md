# RBAC 权限系统

一个基于 Java 21、Spring Boot 和 Vue 3 的 RBAC 权限管理系统。

当前仓库处于**工程骨架阶段**：已经创建后端模块、前端工程和本地容器配置，但暂未实现登录、权限计算、用户管理等业务功能。

## 技术栈

- 后端：Java 21、Spring Boot 3.x、Maven
- 数据访问：MyBatis、MySQL 8、Flyway
- 缓存：Redis
- 前端：Vue 3、TypeScript、Vite、Vue Router、Pinia
- 本地运行：Docker Compose

## 目录结构

```text
.
├── rbac-api/          公共接口、DTO、事件、注解和基础契约
├── rbac-core/         领域模型、权限计算和持久化（待实现）
├── rbac-auth/         登录、令牌和密码处理（待实现）
├── rbac-guard/        请求认证和权限拦截（待实现）
├── rbac-cache/        本地缓存和失效通知（待实现）
├── rbac-audit/        审计记录（待实现）
├── rbac-admin/        管理端 REST 接口（待实现）
├── rbac-bootstrap/    Spring Boot 启动和模块装配
├── web/               Vue 3 前端工程
├── docs/              需求、设计和协作部署文档
├── Dockerfile         后端镜像构建文件
├── compose.yml        本地 MySQL、Redis 和应用编排
└── pom.xml            Maven 多模块聚合工程
```

## 环境要求

本地开发需要：

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm 10+
- Docker Desktop（仅使用 Compose 或容器运行依赖服务时需要）

## 快速开始

### 1. 获取代码

```bash
git clone <仓库地址>
cd ooad
```

### 2. 验证后端骨架

```bash
mvn -B test
mvn -B package
```

打包后的启动 JAR 位于：

```text
rbac-bootstrap/target/rbac-bootstrap-0.1.0-SNAPSHOT.jar
```

本地默认 profile 不连接 MySQL 和 Redis，只用于验证 Spring Boot 骨架可以启动。

### 3. 验证前端骨架

```bash
cd web
npm ci
npm run type-check
npm run build
```

启动前端开发服务器：

```bash
npm run dev
```

默认地址为 `http://localhost:5173`。

### 4. 使用 Docker Compose

在项目根目录执行：

```bash
docker compose up --build
```

默认服务：

| 服务 | 地址 | 用途 |
|---|---|---|
| `rbac-app` | `http://localhost:8080` | 后端应用 |
| `mysql` | `localhost:3306` | 开发数据库 |
| `redis` | `localhost:6379` | 开发缓存 |

健康检查地址：

```text
http://localhost:8080/actuator/health
```

Compose 中的密码仅用于本地开发，不能直接用于测试或生产环境。生产配置、JWT 密钥和数据库密码不得提交到仓库。

停止本地服务：

```bash
docker compose down
```

## 文档入口

开始编码前请先阅读：

1. [编码代理工作基线](./docs/00-编码代理工作基线.md)
2. [需求规格说明书](./docs/01-需求规格说明书.md)
3. [总体设计说明书](./docs/02-总体设计说明书.md)
4. [数据模型设计](./docs/03-数据模型设计.md)
5. [接口设计](./docs/04-接口设计.md)
6. [RBAC0 到 RBAC3 演进设计](./docs/05-RBAC0到RBAC3演进设计.md)
7. [性能与容量设计](./docs/06-性能与容量设计.md)
8. [团队协作与 Linux 部署](./docs/08-团队协作与Linux部署.md)

完整文档索引见 [docs/README.md](./docs/README.md)。

## 当前状态

- [x] Maven 多模块工程骨架
- [x] Vue 3 前端工程骨架
- [x] 公共接口、注解和事件契约占位
- [x] Dockerfile 和 Docker Compose 开发配置
- [x] 后端测试和打包流程
- [x] 前端类型检查和构建流程
- [ ] Flyway 数据库迁移
- [ ] 登录和 JWT
- [ ] RBAC0 权限计算
- [ ] 权限缓存和 Redis 失效通知
- [ ] 管理端接口
- [ ] 前端业务页面
- [ ] CI/CD 配置

## 协作规则

- 不直接向 `main` 和 `develop` 推送；
- 一个任务使用一个 `feature` 或 `fix` 分支；
- 合并前必须通过自动检查并由队友评审；
- 生产环境只发布已经批准的固定版本；
- 详细流程见 [团队协作与 Linux 部署](./docs/08-团队协作与Linux部署.md)。
