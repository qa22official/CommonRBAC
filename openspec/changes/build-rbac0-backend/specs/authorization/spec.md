# Spec Delta

## Purpose

提供接口级、菜单级、按钮级的统一鉴权入口：固定顺序的过滤器 + 拦截器链路、PermissionResolver 权限码集合解析、默认拒绝策略、三种鉴权注解的判定，以及 SUPER_ADMIN 等内置保护规则。

## ADDED Requirements

### Requirement: 固定顺序鉴权链路
鉴权主链路 SHALL 固定为：JWT 过滤器（验签 + 身份快照校验）→ 权限拦截器（注解判定 + 调用 PermissionResolver + 权限码匹配）→ 业务方法。链路中的任一节点失败 MUST NOT 继续向后执行。

#### Scenario: 链路前一步失败
- **WHEN** JWT 过滤器返回 401
- **THEN** 请求 MUST NOT 进入拦截器或业务方法

#### Scenario: 全链路通过
- **WHEN** JWT 验签通过、身份快照匹配、注解判定通过、权限码集合包含所需码
- **THEN** 业务方法被调用

### Requirement: 默认拒绝
未标注 `@PublicApi` / `@RequiresLogin` / `@RequiresPermission` 三注解之一的接口 SHALL 默认返回 403 并记录告警，该告警 MUST 可观测（计数 + 按接口归类）。

#### Scenario: 漏加注解
- **WHEN** 请求命中未标注三注解的接口
- **THEN** 返回 403，业务方法未执行

### Requirement: 三注解判定语义
`@PublicApi` SHALL 直接放行（包含未登录）；`@RequiresLogin` SHALL 只校验已认证；`@RequiresPermission` SHALL 接受 API 类权限码或通配码（`*`、`sys:*`、`sys:user:*`）。引用 MENU/BUTTON 类权限码 SHALL 被构建门禁拦截。

#### Scenario: 通配权限码放行
- **WHEN** 用户权限集合包含 `sys:*`、接口需要 `sys:user:create`
- **THEN** 鉴权通过

#### Scenario: 注解引用 MENU 码
- **WHEN** 开发者将 MENU 类权限码写入 `@RequiresPermission`
- **THEN** 构建门禁拒绝编译

### Requirement: PermissionResolver 契约
`PermissionResolver` SHALL 暴露唯一方法 `Set<String> resolve(long userId)`；返回不可变集合；MUST NOT 返回 null；MUST NOT 抛出与权限无关的异常。权限集合的计算只允许出现在 `PermissionResolver` 的实现中，业务代码、拦截器、前端一律只消费集合。

#### Scenario: 正常调用
- **WHEN** 拦截器调用 `resolve(userId)`
- **THEN** 返回包含该用户全部有效权限码（含通配展开后）的不可变集合

#### Scenario: 路径内不做持久访问
- **WHEN** 鉴权主链路正常运行（缓存命中）
- **THEN** 该次请求期间 MUST NOT 发生数据库或 Redis 访问

### Requirement: SUPER_ADMIN 内置保护
系统 SHALL 保持至少一个启用的 SUPER_ADMIN；其权限集合 MUST NOT 移除 `*`；任何用户 MUST NOT 删除或停用自己或最后一个管理类角色。这些规则通过 `ConstraintValidator` 校验链执行，校验链只读不写。

#### Scenario: 只剩一个 SUPER_ADMIN 时停用
- **WHEN** 系统仅剩一个启用的 SUPER_ADMIN 且请求停用该账号
- **THEN** 校验链拒绝并返回错误

#### Scenario: 普通管理员尝试移除 SUPER_ADMIN 的 `*`
- **WHEN** 权限管理员对 SUPER_ADMIN 绑定的角色执行"移除全部权限"
- **THEN** 校验链拒绝该覆盖操作

### Requirement: 权限判定的外部依赖隔离
鉴权主链路 MUST NOT 访问数据库或 Redis；仅在缓存未命中（首次请求或失效后）时，`PermissionResolver` 实现可以触发单飞式持久化读取。业务代码 MUST NOT 出现角色名、角色编码、`if (isSuperAdmin)` 分支或直接判断身份以外的安全逻辑。

#### Scenario: 鉴权耗时预算
- **WHEN** 缓存命中
- **THEN** 鉴权 P99 耗时 ≤ 1ms（服务器侧）

#### Scenario: 缓存未命中
- **WHEN** 该用户首次鉴权或缓存被标记失效
- **THEN** 链路在 ≤ 100ms P99 内完成（含一次持久化读取），且只发生一次（单飞）
