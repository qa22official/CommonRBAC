# Spec Delta

## Purpose

提供构建期与运行期的质量门禁，确保模块依赖、权限注解覆盖、权限码定义一致性、审计声明、权限计算单测路径、越权集成测试、关键 SQL 索引等全部达标。

## ADDED Requirements

### Requirement: ArchUnit 模块依赖校验
构建期 SHALL 通过 ArchUnit 强制模块间只允许依赖 `rbac-api`；`rbac-bootstrap` 可依赖全部；业务模块 MUST NOT 引用 `rbac-core` / `rbac-cache` / `rbac-audit` 等内部类型。违反 MUST 导致构建失败。

#### Scenario: 业务模块误引用 rbac-core
- **WHEN** 业务模块的代码出现对 `rbac-core` 类的 import
- **THEN** ArchUnit 在构建期拦截并报告具体违规类

### Requirement: 权限注解覆盖检查
构建期 SHALL 校验每个 `@RequestMapping` / `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` 方法必须标注三种鉴权注解之一（`@PublicApi` / `@RequiresLogin` / `@RequiresPermission`）。违反 MUST 导致构建失败。

#### Scenario: 未标注解
- **WHEN** 一个新写的管理端接口漏加鉴权注解
- **THEN** 构建报告指出"方法 XXX 未声明鉴权"并失败

### Requirement: 权限码一致性检查
代码中引用的权限码 MUST 在初始化 Flyway 脚本中定义。引用 `sys:*` 这类通配码 MUST 只出现在框架常量中。检查失败 MUST 导致构建失败。

#### Scenario: 新权限码未定义
- **WHEN** 开发在 `@RequiresPermission("biz:order:approve")` 中引用了 `biz:order:approve`，但 Flyway 脚本中不存在
- **THEN** 构建期拦截并列出"biz:order:approve 未定义"

### Requirement: 审计声明检查
业务接口 MUST 显式声明审计意图（调用 `AuditRecorder` 或 `@NoAudit("原因")`）。检查失败 MUST 导致构建失败。

#### Scenario: 未声明审计
- **WHEN** 业务接口既未调用 `AuditRecorder` 也未标注 `@NoAudit`
- **THEN** 构建报告列出未声明接口并失败

### Requirement: 权限计算单测路径覆盖
权限计算 MUST 具备单元测试覆盖以下路径：无角色、单角色、多角色、停用角色、停用权限、超级管理员（含 `*` 通配）、Redis 广播后缓存刷新。全部路径 MUST 有断言，缺少 MUST 被门禁拦截。

#### Scenario: 停用角色
- **WHEN** 用户绑定的角色之一被停用
- **THEN** 权限计算结果不再包含该角色对应的权限码

#### Scenario: 超级管理员
- **WHEN** 用户持有 `*` 权限码
- **THEN** 对任意所需权限码的判定返回通过

### Requirement: 越权集成测试
构建期 SHALL 运行至少一条低权账号访问高权接口的集成测试，期望返回 403。门禁 MUST 保证该测试通过。

#### Scenario: 权限管理员尝试读审计日志
- **WHEN** 只有 `sys:user:*`、`sys:role:*`、`sys:perm:*` 权限的管理员调用审计查询接口（需要 `sys:audit:read`）
- **THEN** 接口返回 403

### Requirement: 关键 SQL EXPLAIN 断言
对用户列表、审计查询、权限重建三类关键 SQL，构建期 SHALL 执行 `EXPLAIN ANALYZE` 并校验执行计划符合预期（索引命中、避免全表扫描）。不符合 MUST 被门禁拦截。

#### Scenario: 审计深翻页无索引
- **WHEN** 审计查询在 `event_time` 范围条件下执行，EXPLAIN 显示全表扫描
- **THEN** 构建失败并提示索引缺失

### Requirement: 性能档位与回归
新增接口 MUST 归入 A 档（P99 ≤ 30ms）、B 档（P99 ≤ 200ms）或例外档。导出类 MUST NOT 归 A 档。A 档接口压测不达标 MUST 阻断发布。

#### Scenario: A 档接口回归
- **WHEN** 压测中 A 档接口 P99 达 45ms
- **THEN** 发布流程中断，记录为未达标

### Requirement: 敏感关键词门禁
日志、异常、响应中 MUST NOT 包含密码/密码哈希/令牌/密钥等敏感字段。构建期 SHALL 扫描源码与测试用例，出现 MUST 被门禁拦截。

#### Scenario: 测试打印令牌
- **WHEN** 某个测试用例把 JWT 直接写入 `System.out.println`
- **THEN** 构建失败并报告具体行
