# Spec Delta

## Purpose

提供用户、角色、权限及其授权关系的持久化与管理接口：增删改查启停、覆盖式授权的乐观锁与行锁并发保护、Flyway 迁移与初始化数据，以及 RBAC0 所需的五张关系表。

## ADDED Requirements

### Requirement: 五张 RBAC 关系表
系统 SHALL 使用 Flyway 管理迁移，实现用户、角色、权限三张主表与 `sys_user_role`、`sys_role_permission` 两张关系表。全部主表 SHALL 采用逻辑删除（`status = 2`），关系表 SHALL 具备唯一约束以防重复。不得使用物理外键；引用校验在事务内完成。

#### Scenario: Flyway 单一序列
- **WHEN** 迁移脚本版本号与已发布脚本冲突
- **THEN** 迁移失败并给出明确冲突提示

#### Scenario: 逻辑删除
- **WHEN** 执行删除操作
- **THEN** 该行 `status` 置为 2，`deleted_at` 写入时间戳，记录保留在表中

### Requirement: 覆盖式授权的并发保护
覆盖式授权接口（覆盖角色的权限集合、覆盖用户的角色集合） SHALL 携带 `expectedVersion`。版本不匹配返回 40950；版本匹配时事务内顺序：`SELECT ... FOR UPDATE` 锁目标行 → 校验约束 → 覆盖写入关系表 + 写 CHANGE 审计 → 提交。必须"先提交事务、后发失效广播"。

#### Scenario: 正常覆盖
- **WHEN** 提交角色 ID、`expectedVersion=12`、新的权限集合
- **THEN** 版本匹配则事务内完成覆盖写入，事务提交后发布 `PERM_CHANGED`，版本号自增

#### Scenario: 并发丢失更新被发现
- **WHEN** 两个客户端对同一角色同时提交覆盖授权请求，二者均携带 `expectedVersion=12`
- **THEN** 恰好一个事务提交成功（版本增至 13），另一个在 `FOR UPDATE` 锁竞争后发现版本仍为 12 或已被前序事务更新，返回 40950

### Requirement: 分页与唯一约束
用户与角色列表 SHALL 支持账号/角色码前缀与状态筛选；权限 SHALL 以树形结构返回。角色 `code`、权限 `code` SHALL 全局唯一且创建后不可修改；创建类接口 SHALL 依赖数据库唯一约束兜底，返回 409 类错误。

#### Scenario: 重复 code
- **WHEN** 提交与已存在角色 `code` 相同的新增请求
- **THEN** 系统拒绝并返回明确的重复提示

#### Scenario: 前缀匹配
- **WHEN** 用户列表接口收到 `keyword=adm`
- **THEN** 返回账号以 `adm` 开头的用户

### Requirement: 内置初始化数据
迁移脚本 SHALL 内置 `SUPER_ADMIN` 角色（绑定权限码 `*`）、`sys` 前缀框架权限码集合（覆盖 `sys:*`）、以及至少一个初始超级管理员账号（默认密码需强制修改）。

#### Scenario: 全新部署首次启动
- **WHEN** 应用在空库上首次启动
- **THEN** Flyway 脚本自动创建五张表、内置角色与框架权限码、以及一个 admin 默认账号

### Requirement: 管理端 CRUD 接口
系统 SHALL 暴露 `/api/users`、`/api/roles`、`/api/permissions` 三组 REST 接口，提供增删改查启停、重置密码、分配/回收角色、分配/回收权限。所有写接口 MUST 通过 `ConstraintValidator` 校验链（含内置保护规则）后才能提交事务。

#### Scenario: 删除仍被引用的角色
- **WHEN** 请求删除已被至少一个用户绑定的角色
- **THEN** 系统拒绝并返回绑定用户数

#### Scenario: 删除仍被角色引用的权限
- **WHEN** 请求删除至少一个角色引用的权限码
- **THEN** 系统拒绝并返回引用角色数
