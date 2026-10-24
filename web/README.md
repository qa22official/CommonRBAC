# CommonRBAC 前端（web）

基于 Vue 3 + TypeScript + Vite + Element Plus 的 RBAC 权限系统前端。

## 技术栈

- Vue 3.5（`<script setup lang="ts">`）
- Vue Router 4（动态路由 + 路由守卫）
- Pinia 2（登录态 / 权限态）
- Element Plus 2 + `@element-plus/icons-vue`
- Axios（统一请求封装）
- Vite 6（`/api` 代理到后端）

## 快速开始

> 前置：Node.js ≥ 18（建议 20+）。本机已装 Node v24。

```bash
cd web
npm install        # 安装依赖
npm run dev        # 启动开发服务器 http://localhost:5173
```

生产构建：

```bash
npm run build      # 类型检查 + 打包，产物在 dist/
```

### Windows 下 `npm` 报“禁止运行脚本”

如果 PowerShell 提示 `npm.ps1 无法加载…禁止运行脚本`，二选一：

1. 改用 `npm.cmd`（例如 `npm.cmd install`）；
2. 或执行一次：`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`。

## 目录结构

```
src/
├── api/            # axios 实例 + 各模块接口封装
│   ├── request.ts  # 统一拦截器（Bearer 令牌、错误码、401 跳登录）
│   ├── auth.ts / user.ts / role.ts / permission.ts / audit.ts
├── types/          # 与后端契约对齐的 TS 类型（camelCase）
├── stores/         # pinia：auth（登录态/权限）、permission（动态菜单）
├── router/         # 静态路由 + 动态路由注册 + 守卫
├── directives/     # v-perm 按钮级权限指令
├── layout/         # 整体布局（侧边栏递归菜单 + 顶栏）
└── views/
    ├── login/      # 登录
    ├── dashboard/  # 首页
    ├── profile/    # 个人中心（修改密码）
    ├── system/     # 用户 / 角色 / 权限管理
    └── audit/      # 审计日志
```

## 与后端联调约定（重要）

前端严格按 `docs/04-接口设计.md` 实现，以下约定需与后端对齐：

### 1. 基础约定

- 基础路径 `/api`，开发环境由 Vite 代理到 `http://localhost:8080`。
- 认证头 `Authorization: Bearer <token>`。
- 统一响应体 `{ code, message, data, traceId }`，`code === 0` 为成功。
- 字段命名 **camelCase**（如 `isBuiltin`、`inheritRoleId`、`permVersion`、`createdAt`）。

### 2. 动态菜单依赖后端 `MENU` 节点的 `path`

侧边栏菜单由 `GET /api/permissions/tree` 返回的 `MENU` 节点动态生成，
`path` 字段必须落在下面这张「路径 → 组件」注册表中：

| path | 页面 |
|---|---|
| `/system/users` | 用户管理 |
| `/system/roles` | 角色管理 |
| `/system/permissions` | 权限管理 |
| `/system/audit` | 审计日志 |

> Flyway 初始化数据里 `sys_permission` 的 `MENU.path` 请与上表保持一致。

### 3. 权限判定

前端权限唯一数据源是 `GET /api/auth/me` 返回的 `permissions` 数组：

- `*` 命中一切；
- `sys:user:*` 等前缀通配；
- 其余走精确匹配。

菜单过滤、路由注册、`v-perm` 均依赖它。

### 4. 覆盖式授权的版本号

`分配角色` / `分配权限` 使用 `expectedVersion` 乐观锁。
收到 `40950`（版本冲突）时前端会重新拉取并提示用户，不会静默重放。

## 错误码处理

| code | 前端行为 |
|---|---|
| `40100` / `40101` | 清除令牌并跳转登录页 |
| `40950` | 提示“数据已被他人修改”并重新加载 |
| 其余非 0 | 表单/页面内 `ElMessage` 提示 |
