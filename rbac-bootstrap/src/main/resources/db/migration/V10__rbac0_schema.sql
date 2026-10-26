-- RBAC0 基础表结构（03 §4）
-- 说明：本脚本同时兼容 MySQL 8 与 H2(MySQL 模式)，因此不使用 ENGINE/CHARSET
-- 子句，也不使用 MySQL 专有的内联 KEY 语法；索引统一用 CREATE INDEX 建立。

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    email VARCHAR(128) NULL,
    phone VARCHAR(32) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    token_version INT NOT NULL DEFAULT 0,
    must_change_password TINYINT NOT NULL DEFAULT 0,
    role_version INT NOT NULL DEFAULT 0,
    fail_count SMALLINT NOT NULL DEFAULT 0,
    locked_until DATETIME(3) NULL,
    last_login_at DATETIME(3) NULL,
    remark VARCHAR(255) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    inherit_role_id BIGINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    is_builtin TINYINT NOT NULL DEFAULT 0,
    perm_version INT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_role_code UNIQUE (code)
);
CREATE INDEX idx_role_inherit_role_id ON sys_role (inherit_role_id);

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(64) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    path VARCHAR(255) NULL,
    http_method VARCHAR(16) NULL,
    icon VARCHAR(64) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    is_builtin TINYINT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_permission_code UNIQUE (code)
);
CREATE INDEX idx_permission_parent_id ON sys_permission (parent_id);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_role_role_id ON sys_user_role (role_id);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permission_permission_id ON sys_role_permission (permission_id);

CREATE TABLE sys_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    audit_category VARCHAR(16) NOT NULL,
    operator_id BIGINT NULL,
    operator_name VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NULL,
    target_id BIGINT NULL,
    detail_before JSON NULL,
    detail_after JSON NULL,
    result TINYINT NOT NULL,
    fail_reason VARCHAR(255) NULL,
    transaction_id VARCHAR(64) NULL,
    client_ip VARCHAR(64) NOT NULL,
    user_agent VARCHAR(512) NULL,
    trace_id VARCHAR(64) NULL,
    event_time DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_audit_event_time ON sys_audit_log (event_time);
CREATE INDEX idx_audit_operator_time ON sys_audit_log (operator_id, event_time);
