# RBAC 管理端（web）

基于 Vue 3 + TypeScript + Vite 的 RBAC 管理后台，使用 Vue Router、Pinia、Element Plus 与 Axios。

## 环境要求

- Node.js 22（当前开发环境为 v22.20.0）
- 后端服务默认运行在 `http://localhost:8080`

## 启动

```bash
cd web
npm install
npm run dev
```

开发服务器默认地址为 `http://localhost:5173`，`/api` 请求由 Vite 代理到
`http://localhost:8080`（见 `vite.config.ts`）。

## 检查与构建

```bash
npm run type-check   # vue-tsc --noEmit
npm run build        # vue-tsc --noEmit && vite build
npm run preview      # 预览 dist 产物
```

要求 TypeScript `strict` 模式通过，不使用 `any` 兜底。

## 页面清单

| 路由 | 页面 | 入口权限码 | 主要功能 |
|---|---|---|---|
| `/login` | 登录 | 无需登录 | 账号密码登录；成功后调用 `/api/auth/me` |
| `/change-password` | 修改密码 | 登录即可 | 强制改密、主动改密；成功后清除令牌回登录页 |
| `/dashboard` | 仪表盘 | 登录即可 | 当前账号、角色、权限码与快捷入口 |
| `/system/users` | 用户管理 | `sys:user` | 查询、分页、新增、编辑、删除、启停、重置密码、分配角色 |
| `/system/roles` | 角色管理 | `sys:role` | 查询、分页、新增、编辑、删除、启停、继承来源、权限树授权 |
| `/system/permissions` | 权限管理 | `sys:perm` | 权限树查询、新增、编辑、删除、父节点与类型维护 |
| `/system/audit-logs` | 审计日志 | `sys:audit` | 时间范围筛选、游标分页、加载更多、前后 JSON 展开 |

未登录访问任意业务页面会跳转 `/login`。`mustChangePassword = true` 时只允许
`/change-password` 和退出登录，不注册业务路由。

## 权限实现

- 权限唯一数据源：`GET /api/auth/me` 返回的 `permissions`。
- 菜单：按 `sys:user` / `sys:role` / `sys:perm` / `sys:audit` 四个 MENU 权限码过滤。
- 路由：登录后按权限动态 `addRoute`；未授权路由不注册，由 404 页面承接。
- 按钮：使用 `v-perm` 指令，无权限直接移除 DOM。
  ```vue
  <el-button v-perm="'sys:user:create'">新增用户</el-button>
  ```
- 通配规则：`*` 命中一切；只有持有通配码时才做前缀匹配，例如 `sys:user:*`
  命中 `sys:user:delete`，但普通权限码只做精确匹配。
- 禁止使用角色编码做权限判断。前端权限仅决定展示，不是安全边界。

## 请求与错误处理

Axios 基础路径为 `/api`，自动附加 `Authorization: Bearer <token>` 和
`X-Trace-Id`，并读取响应头 `X-Trace-Id`。

| 错误码 | 处理 |
|---|---|
| HTTP 401 / `40100` / `40101` | 清除本地令牌、跳转登录页并提示“登录状态已失效” |
| `40300` 或 HTTP 403 | 提示“无操作权限” |
| `40950` | 提示“数据已被他人修改”，用户、角色授权页面重新拉取最新数据 |
| `42900` | 页面顶部展示重试倒计时（优先使用 `Retry-After`） |
| `50300` | 提示“系统暂时不可用，请稍后重试” |
| `50000` / HTTP 5xx | 提示“系统繁忙”并展示 traceId |

`40900` / `40910` / `40920` / `40310` 等业务错误直接展示后端返回的中文 message。

## 目录结构

```text
web/src/
├── api/          Axios 实例与各接口封装
├── components/   密码强度、429 倒计时等通用组件
├── directives/   v-perm 指令
├── layouts/      主布局（侧边菜单 + 顶栏）
├── router/       静态路由、动态业务路由、守卫
├── stores/       auth 与限流状态
├── types/        接口与路由类型声明
├── utils/        权限匹配、密码强度、格式化、常量
└── views/        登录、改密、仪表盘、系统管理页面
```

## 接口约定与假设

- 审计日志查询参数使用文档 §6 的 `auditCategory` / `result`（`1` 成功 /
  `0` 失败），响应项按 `AuditLogItem` 读取 `category` 与 `success`。
- `/api/roles/options` 按 `RoleSummary[]`（`id/code/name`）处理。
- 角色继承字段按后端 DTO 的 `inheritRoleId` / `inheritRoleName` 处理；
  表单清空继承时提交 `inheritRoleId: null`。
- 权限授权使用 `check-strictly` 的权限树，父子节点独立勾选；提交时发送当前
  勾选的全部 `permissionCodes`，不做父子联动隐式放大。
- 仪表盘不调用统计接口，仅展示 `/api/auth/me` 返回的真实会话数据，避免展示
  前端伪造指标。
