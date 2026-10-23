-- V220 · 内置数据
-- 兼容 MySQL 8.x + H2 MODE=MySQL
-- SUPER_ADMIN（绑定 *）、AUDITOR（绑定 sys:audit:read）、sys:* 框架权限码、admin 初始账号
-- 初始密码：Admin@123  （首次登录后必须立即修改）
-- BCrypt hash: $2a$10$CUYOUvV/I7g9GXWlPq.mGeqFwslLXqjmFda4WIKsT5P2A0EL93adG

INSERT INTO sys_user (username, nickname, password_hash, token_version, status, role_version, builtin, created_by, created_at, updated_by, updated_at)
VALUES ('admin', '超级管理员',
        '$2a$10$CUYOUvV/I7g9GXWlPq.mGeqFwslLXqjmFda4WIKsT5P2A0EL93adG',
        0, 1, 0, 1, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP);

INSERT INTO sys_role (code, name, description, status, perm_version, inherit_role_id, builtin, created_by, created_at, updated_by, updated_at)
VALUES ('SUPER_ADMIN', '超级管理员', '系统内置不可删除的最高权限角色', 1, 1, 0, 1, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('AUDITOR',     '审计员',     '只读审计日志',                     1, 1, 0, 1, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP);

INSERT INTO sys_permission (code, name, parent_id, category, status, sort, created_by, created_at, updated_by, updated_at)
VALUES ('*',                      '全部权限',            0, 'SYSTEM',  1, 1,   0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:user:read',          '用户列表',            0, 'API',     1, 10,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:user:create',        '创建用户',            0, 'API',     1, 11,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:user:update',        '更新用户',            0, 'API',     1, 12,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:user:delete',        '删除用户',            0, 'API',     1, 13,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:user:assign-role',    '分配角色',            0, 'API',     1, 14,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:user:reset-password', '重置密码',           0, 'API',     1, 15,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:role:read',          '角色列表',            0, 'API',     1, 20,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:role:create',        '创建角色',            0, 'API',     1, 21,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:role:update',        '更新角色',            0, 'API',     1, 22,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:role:delete',        '删除角色',            0, 'API',     1, 23,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:role:assign-perm',   '分配权限',            0, 'API',     1, 24,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:perm:read',          '权限列表',            0, 'API',     1, 30,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:perm:create',        '创建权限',            0, 'API',     1, 31,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:perm:update',        '更新权限',            0, 'API',     1, 32,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:perm:delete',        '删除权限',            0, 'API',     1, 33,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:audit:read',         '查询审计日志',        0, 'API',     1, 40,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:auth:login',         '登录',                0, 'API',     1, 50,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:auth:logout',        '登出',                0, 'API',     1, 51,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:auth:refresh',       '刷新令牌',            0, 'API',     1, 52,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP),
       ('sys:auth:change-password', '修改密码',          0, 'API',     1, 53,  0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP);

-- admin ↔ SUPER_ADMIN
INSERT INTO sys_user_role (user_id, role_id, created_at)
SELECT u.id, r.id, CURRENT_TIMESTAMP
  FROM sys_user u, sys_role r
 WHERE u.username = 'admin' AND r.code = 'SUPER_ADMIN';

-- SUPER_ADMIN ↔ *
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
  FROM sys_role r, sys_permission p
 WHERE r.code = 'SUPER_ADMIN' AND p.code = '*';

-- AUDITOR ↔ sys:audit:read
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
  FROM sys_role r, sys_permission p
 WHERE r.code = 'AUDITOR' AND p.code = 'sys:audit:read';
