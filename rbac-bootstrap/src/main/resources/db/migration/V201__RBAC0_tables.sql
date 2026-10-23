-- V201 · RBAC0 五表
-- 兼容 MySQL 8.x + H2 MODE=MySQL
-- 命名：sys_user / sys_role / sys_permission / sys_user_role / sys_role_permission
-- 约束：逻辑删除 status=2；不使用物理外键

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    nickname      VARCHAR(128) DEFAULT NULL,
    password_hash VARCHAR(128) NOT NULL,
    token_version BIGINT       NOT NULL DEFAULT 0,
    status        TINYINT      NOT NULL DEFAULT 1,
    role_version  BIGINT       NOT NULL DEFAULT 0,
    builtin       TINYINT      NOT NULL DEFAULT 0,
    created_by    BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    BIGINT       NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP    DEFAULT NULL,
    UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512) DEFAULT NULL,
    status          TINYINT      NOT NULL DEFAULT 1,
    perm_version    BIGINT       NOT NULL DEFAULT 0,
    inherit_role_id BIGINT       NOT NULL DEFAULT 0,
    builtin         TINYINT      NOT NULL DEFAULT 0,
    created_by      BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT       NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP    DEFAULT NULL,
    UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(128) NOT NULL,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    category    VARCHAR(16)  NOT NULL DEFAULT 'API',
    status      TINYINT      NOT NULL DEFAULT 1,
    sort        INT          NOT NULL DEFAULT 0,
    created_by  BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP    DEFAULT NULL,
    UNIQUE (code),
    INDEX idx_category (category)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id    BIGINT    NOT NULL,
    role_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    INDEX idx_role_id (role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id      BIGINT    NOT NULL,
    permission_id BIGINT   NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    INDEX idx_permission_id (permission_id)
);
