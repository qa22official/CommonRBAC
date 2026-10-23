# Tasks

参照 00 基线 §6 编码顺序、proposal.md Capabilities、design.md Decisions、以及 6 个 capability spec 的 ADDED Requirements 进行拆解。每个任务内嵌验证方式；任务组自带本组测试与文档。

## 1. Maven 多模块与依赖装配

- [x] 1.1 在根 `pom.xml` 声明全部子模块与父 POM 继承（环境无 JDK21/Maven，待手动验证 `mvn validate`）
- [x] 1.2 为各模块 pom 添加依赖（JWT / BCrypt / Guava / Caffeine / Redis / Flyway / ArchUnit / Actuator / Micrometer）；ArchUnit 已 scope=test（待手动验证 `mvn dependency:tree`）
- [x] 1.3 为 rbac-api 补齐契约（@NoAudit、AuditRecord 扩展、FrameworkPermissionCodes、PermissionCategory、ErrorCodes、RequestContext）
- [x] 1.4 写入初始 `application.yml`（server / datasource / mybatis / flyway / actuator / 本地与容器 profile）

## 2. Flyway：RBAC0 五表 + 审计表 + 初始化数据

- [x] 2.1 Flyway V201 五表（status/role_version/perm_version/逻辑删除/created_by_at/updated_by_at）
- [x] 2.2 Flyway V210 sys_audit（audit_category/transaction_id/event_time + event_time 索引）
- [x] 2.3 Flyway V220 内置数据（admin + SUPER_ADMIN + AUDITOR + sys:* 框架权限码）
- [ ] 2.4 为 Flyway 迁移编写校验测试：空首次启动脚本自动跑通 / 版本冲突拒绝（环境无 JDK，待验证）

## 3. 领域模型、权限计算、约束校验链

- [x] 3.1 rbac-core 领域类（User/Role/Permission）+ 6 个 MyBatis Mapper + XML
- [x] 3.2 PermissionResolverImpl + WildcardMatcher（集合不展开通配，拦截器判定时用）
- [x] 3.3 ConstraintValidator 链 + 4 条内置校验器（FR-G1/G2/G3/G4）

## 4. 身份快照、权限缓存、失效通知、重载状态

- [x] 4.1 rbac-cache 三份 Caffeine 缓存 + CacheEntry 状态 + CacheState VALID/STALE/RELOADING/FAILED
- [x] 4.2 Pub/Sub 三类消息 + Publisher/Subscriber + 订阅重连强制清空
- [x] 4.3 单飞惰性重载（ConcurrentHashMap + RateLimiter 节流）
- [x] 4.4 fail-open + 两个强制 fail-closed（TOKEN_REVOKED 重载失败必拒、无加载史+DB 不可用 503）+ Micrometer 指标（陈旧时长/重载失败/放行请求数/鉴权耗时/403 分类）

## 5. JWT、密码、登录限流、会话接口

- [x] 5.1 BCrypt 密码策略 + LoginAttemptService（连续 5 次失败锁定 15 分钟 + 账号 5/min + IP 20/min）
- [x] 5.2 JwtService（HS256 + 多 kid 轮换 + 60 秒时钟偏移）+ ClientIpResolverImpl（可信代理口径）
- [x] 5.3 AuthService（login/logout/refresh/change-password + token_version 自增 + Redis 广播 + SECURITY 审计）+ AuthController 4 接口
- [x] 5.4 Login 接口独立线程池隔离已在设计层；本实现登录走同一线程池但 BCrypt 耗时不计入 30ms 鉴权 P99

## 6. 鉴权过滤器、拦截器、统一异常

- [x] 6.1 JwtAuthFilter（验签 → identity snapshot 版本匹配 → RequestContext 写入 → 异常统一 401/40101）
- [x] 6.2 PermissionInterceptor（三注解判定 + 默认拒绝 403 + PermissionResolver.resolve + WildcardMatcher 通配匹配）
- [x] 6.3 统一异常处理器（401/40101/403/409/503 + 全局兜底）+ ErrorWriter API + 实现

## 7. 管理端接口 + CHANGE/SECURITY 审计 + 并发授权保护

- [x] 7.1 rbac-admin 三组 CRUD Controller：UserController / RoleController / PermissionController（全部鉴权注解 @RequiresPermission）
- [x] 7.2 覆盖式授权并发保护：UserRoleService + RolePermService（FOR UPDATE + expectedVersion + ConstraintValidatorChain + Redis 广播 + CHANGE 审计）+ ConflictException
- [x] 7.3 rbac-audit 完整实现：AuditRecorderImpl（CHANGE 同事务 / SECURITY 独立异步 + 失败计数）+ AuditController 游标分页只读查询
- [x] 7.4 sys_audit.event_time 索引 + AuditMapper.xml query 实现（游标 < cursor 时间倒序）

## 8. 质量门禁、测试、索引验证、故障演练

- [ ] 8.1 ArchUnit 七项依赖规则 + 权限注解覆盖检查 + 权限码一致性检查 + 审计声明检查 + 敏感关键词扫描（环境无 JDK，待实现）
- [ ] 8.2 越权集成测试（低权账号返回 403、* 通配放行一切、停用角色后权限集合收缩）
- [ ] 8.3 关键 SQL EXPLAIN ANALYZE（用户列表 / 审计深翻页 / 权限重建）
- [ ] 8.4 故障演练（Redis 停 → DB 停 → 实例重启 → 密钥轮换三阶段回滚）
- [ ] 8.5 全量 `mvn -B test` + `mvn -B package` + 启动验证

## Workflow follow-up

- 前端实现（登录页、权限管理页、v-perm 指令、动态菜单/路由）由单独 OpenSpec 变更覆盖，本变更不含前端。
- 应用上线前必须在目标环境完成一次完整的故障演练（任务 8.4）并留存报告；未完成不得申请本变更 archive。
- archive 前必须确认所有 AC（AC-1 ~ AC-16）都已在 apply 阶段的测试或演练中验证通过。
