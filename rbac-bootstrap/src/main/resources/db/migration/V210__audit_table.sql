-- V210 · sys_audit 审计表
-- 兼容 MySQL 8.x + H2 MODE=MySQL
-- event_time 必须建索引（04 §5.7 + quality-gates spec 7）

CREATE TABLE IF NOT EXISTS sys_audit (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    audit_category VARCHAR(16)   NOT NULL,
    event_type     VARCHAR(64)   NOT NULL,
    transaction_id VARCHAR(128)  NOT NULL,
    operator_id    BIGINT        DEFAULT NULL,
    operator_name  VARCHAR(128)  DEFAULT NULL,
    target_type    VARCHAR(64)   NOT NULL,
    target_id      VARCHAR(64)   DEFAULT NULL,
    detail_before  CLOB          DEFAULT NULL,
    detail_after   CLOB          DEFAULT NULL,
    result         VARCHAR(16)   NOT NULL,
    fail_reason    VARCHAR(512)  DEFAULT NULL,
    client_ip      VARCHAR(64)   DEFAULT NULL,
    user_agent     VARCHAR(512)  DEFAULT NULL,
    trace_id       VARCHAR(128)  DEFAULT NULL,
    event_time     TIMESTAMP     NOT NULL,
    INDEX idx_event_time (event_time),
    INDEX idx_category_time (audit_category, event_time),
    INDEX idx_operator (operator_id),
    INDEX idx_target (target_type, target_id)
);
