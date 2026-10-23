# Spec Delta

## Purpose

提供账号密码认证能力：登录、登出、续期、改密、失败锁定、IP 与账号双维度限流；签发 HS256 JWT 令牌并支持 kid 轮换；通过 `token_version` 机制让历史令牌在指定时效内失效。

## ADDED Requirements

### Requirement: 账号密码登录校验
系统 SHALL 在登录接口校验账号状态、连续失败次数、限流（账号 5 次/分钟、IP 20 次/分钟）和 BCrypt 密码哈希。通过后 SHALL 将该账号的 `token_version` 自增以作废所有历史令牌。登录与密码校验 SHALL 运行于独立线程池，使登录接口不受鉴权类 30ms P99 约束。

#### Scenario: 登录成功
- **WHEN** 账号密码正确、账号启用、未超出失败阈值与限流阈值
- **THEN** 系统返回 JWT 访问令牌（2 小时有效期）、自增该账号的 `token_version`、写入 SECURITY 审计事件

#### Scenario: 密码错误
- **WHEN** 密码校验失败
- **THEN** 系统返回不区分账号是否存在的失败提示、连续失败计数加 1

#### Scenario: 连续失败 5 次
- **WHEN** 连续失败计数达到 5
- **THEN** 账号被锁定 15 分钟，期间拒绝所有登录尝试

#### Scenario: IP 维度限流触发
- **WHEN** 同一 IP 在一分钟内发起超过 20 次登录尝试
- **THEN** 系统拒绝后续尝试并记录来源 IP

### Requirement: JWT 签发与验签
JWT SHALL 使用 HS256 签名，密钥 ≥ 256 位并通过环境变量下发。载荷 SHALL 包含 `sub`（userId）、`ver`（tokenVersion）、`kid`、`iss`、`aud`、`iat`、`exp`，MUST NOT 包含角色或权限明细。验签 SHALL 依据 `kid` 选择当前生效密钥，并允许 60 秒时钟偏移。

#### Scenario: 验签通过
- **WHEN** 令牌签名合法、`kid` 已配置、`iss`/`aud` 匹配、`iat`/`exp` 在允许窗口内
- **THEN** 鉴权主链路继续执行身份快照校验

#### Scenario: kid 不匹配
- **WHEN** 令牌 `kid` 在当前实例密钥配置中找不到
- **THEN** 验签失败，返回 401

#### Scenario: 密钥轮换期间双密钥同时生效
- **WHEN** 配置同时包含旧密钥与新密钥
- **THEN** 实例同时支持两种签名的验签，签发方切换到新密钥时已有旧令牌仍然有效直至过期

### Requirement: 登出、改密、停用触发令牌作废
登出、修改密码、账号停用、重置密码、全局密钥泄露场景 SHALL 使该账号（或全局）的历史令牌在规定时效内失效。失效通过自增 `token_version`（全局作废自增基线）并广播 `TOKEN_REVOKED` / `TOKEN_REVOKED_GLOBAL` 实现。

#### Scenario: 正常作废时效
- **WHEN** Redis 可用
- **THEN** 后续请求在 ≤ 1 秒内被拒绝（40101）

#### Scenario: Redis 不可用
- **WHEN** Redis 故障或广播丢失
- **THEN** 后续请求在 ≤ 30 秒内（TTL 兜底）被拒绝

#### Scenario: 实例重启
- **WHEN** 鉴权实例重启后收到历史令牌
- **THEN** 首个请求因缓存为空直接读库并立即拒绝已作废令牌

### Requirement: 登录与密码安全约束
密码 SHALL 以 BCrypt（强度 10~12）存储；禁止 MD5/SHA1/明文。密码策略 SHALL 要求长度 ≥ 8、含大小写字母与数字、不得与账号相同。密码哈希、令牌、密钥 MUST NOT 出现在日志、响应、异常或审计快照中。

#### Scenario: 密码策略不符合
- **WHEN** 用户提交的新密码违反策略
- **THEN** 系统拒绝并返回具体缺失项

#### Scenario: 敏感字段脱敏
- **WHEN** 任何异常堆栈、审计记录或响应可能包含密码/令牌
- **THEN** 实际落库或输出的内容中这些字段已被完全脱敏（不存在）

### Requirement: 续期接口静默刷新令牌
访问令牌 2 小时期满前的续期请求 SHALL 重新读取最新 `token_version` 与账号状态，生成新令牌并使旧令牌在 ≤ 30 秒内失效。

#### Scenario: 正常续期
- **WHEN** 在访问令牌有效期内提交续期请求
- **THEN** 系统返回新的 JWT（同 `sub`，新 `iat`/`exp`/`ver`）

#### Scenario: 账号续期前被停用
- **WHEN** 续期请求抵达时账号已被停用
- **THEN** 系统拒绝续期并返回 401

#### Scenario: 密钥轮换与续期并存
- **WHEN** 密钥处于轮换期间、续期请求携带旧 `kid` 的令牌
- **THEN** 实例能验签旧令牌、用新密钥签发新令牌

### Requirement: 客户端 IP 统一解析
系统 SHALL 由 `ClientIpResolver` 单一组件负责解析客户端 IP，仅信任可信代理网段的 `X-Forwarded-For`。限流、审计、告警 MUST 复用同一解析结果。应用端口不得对公网直接开放。

#### Scenario: 反向代理场景
- **WHEN** 请求经可信 Nginx 反代后抵达应用
- **THEN** `ClientIpResolver` 返回真实来源 IP，用于限流计数与审计

#### Scenario: 伪造 X-Forwarded-For
- **WHEN** 伪造的 `X-Forwarded-For` 链包含应用自身而非可信代理
- **THEN** 该伪造头被忽略，使用直接连接的对端 IP
