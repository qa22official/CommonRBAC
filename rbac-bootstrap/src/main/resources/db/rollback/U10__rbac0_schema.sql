-- 手工回滚脚本：不参与 Flyway 自动执行（03 §9）。
DROP TABLE IF EXISTS sys_audit_log;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
