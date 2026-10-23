# Spec Delta

## Purpose

提供鉴权所需的本地权限缓存与 Redis Pub/Sub 失效广播：三份事实映射（roleId→codes、userId→roleIds、userId→{tokenVersion,status}）、TTL 兜底、fail-open 姿态、单飞惰性重载，以及一组强制可观测指标。

## ADDED Requirements

### Requirement: 三份本地缓存分离
每实例 SHALL 持有三份独立缓存：roleId → Set<permissionCode>（缓存 A）、userId → Set<roleId>（缓存 B）、userId → IdentitySnapshot（缓存 C）。修改角色权限只清空 A；停用用户只丢弃 C 中该条目；修改用户角色同时清空 A 与 B。

#### Scenario: 角色权限变更只影响 A
- **WHEN** 某角色的权限集合被覆盖
- **THEN** 只有缓存 A 被标记失效，缓存 B、C 保持不变

#### Scenario: 停用用户只影响 C
- **WHEN** 管理员停用账号 7
- **THEN** 只丢弃缓存 C 中 userId=7 的条目

### Requirement: Redis Pub/Sub 失效广播
写操作事务提交成功后 SHALL 发布三类消息：`PERM_CHANGED`（缓存 A、B）、`TOKEN_REVOKED`（缓存 C 单用户）、`TOKEN_REVOKED_GLOBAL`（缓存 C 全体）。订阅方收到消息后只标记失效，不立即重载，由后续请求触发惰性重载。1 秒内多次变更 SHALL 合并为一次（广播去抖）。

#### Scenario: 广播先于事务提交（反序异常）
- **WHEN** 某次实现错误地在事务提交前发广播
- **THEN** 收到"已标记失效"的订阅方可能以旧数据重建缓存（本变更仅通过"先提交事务、后发广播"的流程约束避免反序）

#### Scenario: 多次快速变更合并
- **WHEN** 某角色在 1 秒内经历三次权限覆盖
- **THEN** Redis 中只出现一条 `PERM_CHANGED` 消息

### Requirement: 30 秒 TTL 兜底
三份缓存均 SHALL 设置 30 秒 `expireAfterWrite`。TTL 到期触发重载但不丢弃数据。TTL 与令牌作废时效（FR-A9）一一对应，调整 TTL MUST 同步修改 FR-A9。

#### Scenario: Redis 广播丢失
- **WHEN** Redis 故障或订阅网络抖动期间恰好发生了一次权限变更
- **THEN** 下次请求在 ≤ 30 秒 TTL 到期后自动读库重载，权限收敛

### Requirement: 缓存元数据与状态机
每个缓存条目 SHALL 携带 `cacheVersion`、`loadedAt`（只由成功加载更新）、`expiresAt`、`state`（VALID / STALE / RELOADING / FAILED）。数据一旦成功加载过 MUST NOT 被丢弃。`loadedAt` MUST NOT 在重载失败时被伪刷新。

#### Scenario: 陈旧时长指标
- **WHEN** 某条缓存因重载失败进入 STALE 状态并持续服务 2 分钟
- **THEN** "当前陈旧时长"指标 MUST 为 2 分钟，`loadedAt` 仍是最初成功加载的时间戳

#### Scenario: RELOADING 状态
- **WHEN** 某个 userId 的缓存正在被一个线程重载
- **THEN** 同一时刻其他命中该 userId 的线程 MUST 直接使用旧数据而非再次发起查库

### Requirement: fail-open 重载失败策略
默认 `rbac.cache.stale-policy=fail-open`：重载失败时继续用最后一份成功数据，告警 + 指标。`TOKEN_REVOKED(X)` 后 X 的快照重载失败 MUST 强制拒绝 X（40101）。数据库不可用且实例从未成功加载 MUST 拒绝全部鉴权请求（503）。

#### Scenario: 数据库临时不可用
- **WHEN** MySQL 不可用、实例已持有有效的陈旧缓存
- **THEN** 鉴权继续放行，陈旧时长持续增加，重载失败计数递增

#### Scenario: 未成功加载过 + 数据库不可用
- **WHEN** 应用刚启动即遭遇数据库故障，尚未加载过任何缓存
- **THEN** 鉴权请求返回 503

### Requirement: 必需可观测指标
系统 MUST 暴露以下 Prometheus 指标：缓存当前陈旧时长、重载失败次数、受影响用户数、用陈旧权限放行的请求数、鉴权耗时直方图（单独埋点）、403 次数（按权限码归类）。

#### Scenario: 故障复盘所需指标齐全
- **WHEN** 发生一次数据库故障持续 5 分钟后恢复
- **THEN** 监控面板能回答：期间有多少请求用陈旧权限放行、影响了多少用户、缓存陈旧时长何时达到峰值
