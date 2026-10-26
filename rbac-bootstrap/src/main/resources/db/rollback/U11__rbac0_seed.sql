-- 手工回滚脚本：仅删除 V11 写入的内置数据，不删除初始 admin 账号。
-- 先清理引用，避免留下悬空的用户-角色关系。
DELETE FROM sys_user_role WHERE role_id IN (1, 2, 3);
DELETE FROM sys_role_permission WHERE role_id IN (1, 2, 3);
DELETE FROM sys_role WHERE id IN (1, 2, 3);
DELETE FROM sys_permission WHERE id BETWEEN 1 AND 26;
