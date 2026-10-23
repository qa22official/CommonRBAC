# Spec Delta

## Purpose

提供两类审计事件的持久化与查询能力：CHANGE 类与业务变更同事务落库，SECURITY 类独立落库不参与业务事务；并确保导出操作记录 scope/resultCount/condition 固定结构。

## ADDED Requirements

### Requirement: CHANGE 与 SECURITY 两类事务语义
CHANGE 类审计 SHALL 与业务变更同事务，审计失败则业务回滚；SECURITY 类独立落库，写入失败 MUST NOT 回滚业务但必须计数与告警。RBAC 域全部写操作 SHALL 归属 CHANGE 类；登录成功/失败、登出、令牌作废、越权访问、限流触发、缓存降级 SHALL 归属 SECURITY 类。

#### Scenario: CHANGE 审计失败回滚业务
- **WHEN** 业务写事务准备提交，同一事务内的审计写入失败
- **THEN** 业务事务回滚，CHANGE 审计无残留

#### Scenario: SECURITY 审计失败不回滚业务
- **WHEN** 登录成功业务已完成，SECURITY 审计独立写入失败
- **THEN** 业务正常提交，SECURITY 写入失败被计数并触发告警

### Requirement: 审计覆盖内容与脱敏
审计记录 MUST 包含：类别、操作人 ID 与名称、操作类型、目标对象类型与 ID、变更前后快照（已脱敏）、事件时间、来源 IP、User-Agent、traceId。密码、密码哈希、令牌、密钥 MUST NOT 进入任何审计快照。

#### Scenario: 快照脱敏
- **WHEN** 对用户执行"修改密码"并记录 CHANGE 审计
- **THEN** `detail_after` 中不存在新密码或密码哈希，仅记录已修改这一事实

### Requirement: 导出类操作固定结构
判定为"导出/下载/打印/外部推送"的操作，其 SECURITY 审计的 `detail_after` MUST 包含固定结构 `{ "scope": "FILTERED|ALL|SINGLE", "resultCount": N, "condition": {...} }`。

#### Scenario: 范围筛选导出
- **WHEN** 管理员按状态筛选导出用户列表，结果 1842 条
- **THEN** `detail_after.scope="FILTERED"`、`resultCount=1842`、`condition` 携带筛选条件

### Requirement: 审计只读与查询
审计日志 SHALL 只读（系统应用账号无删除/修改权限）；查询接口 SHALL 支持按类别、时间范围、操作人、操作类型、目标对象筛选，并采用游标分页。默认保留 180 天；归档与清理动作本身 SHALL 记录系统日志。

#### Scenario: 审计删除被拒
- **WHEN** 任何 HTTP 客户端请求删除一条审计记录
- **THEN** 系统返回 403，日志条目保持不变

#### Scenario: 游标分页
- **WHEN** 审计查询接口收到 `cursor=last-event-time`、`limit=20`
- **THEN** 返回不超过 20 条记录与下一页游标

### Requirement: 业务接口审计意图声明
每个业务接口 MUST 显式声明审计意图：在方法体内调用 `AuditRecorder`，或在方法上标注 `@NoAudit("原因")`（value 必填且非空）。两者皆无 MUST 被构建门禁拦截。

#### Scenario: 门禁拦截未声明的接口
- **WHEN** 构建时检测到业务接口既未调用 `AuditRecorder` 也未标注 `@NoAudit`
- **THEN** 构建失败并报告具体类/方法

### Requirement: 业务域脱敏责任边界
审计白名单内的业务域快照脱敏 SHALL 由各业务模块自行负责；统一脱敏器仅能处理 RBAC 域字段。审计接口只接受已脱敏数据。

#### Scenario: 业务模块忘记脱敏
- **WHEN** 业务模块调用 `AuditRecorder` 时传入的快照包含敏感业务字段
- **THEN** 审计写入仍然发生，但敏感字段出现在审计中形成记录残留风险（本变更仅通过接口自查清单而非运行期拦截管理该风险）
