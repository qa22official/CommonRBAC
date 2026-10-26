-- RBAC 域内置权限点、角色与授权（03 §8、04 §7）
-- 初始 admin 不在此脚本中创建，密码由启动器从 RBAC_ADMIN_PASSWORD 读取。

INSERT INTO sys_permission
    (id, code, name, resource_type, parent_id, path, http_method, icon, status, is_builtin, sort, created_at, updated_at)
VALUES
    (1,  '*',                     '全部权限',       'SYSTEM', 0,  NULL,                          NULL, NULL,   1, 1, 0,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (2,  'sys:user',              '用户管理',       'MENU',   0,  '/system/users',               NULL, 'user', 1, 1, 10, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (3,  'sys:user:read',         '查询用户',       'API',    2,  '/api/users',                  'GET', NULL,  1, 1, 1,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (4,  'sys:user:create',       '新增用户',       'API',    2,  '/api/users',                  'POST', NULL, 1, 1, 2,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (5,  'sys:user:update',       '编辑用户',       'API',    2,  '/api/users/{id}',             'PUT', NULL,  1, 1, 3,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (6,  'sys:user:delete',       '删除用户',       'API',    2,  '/api/users/{id}',             'DELETE', NULL, 1, 1, 4, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (7,  'sys:user:toggle',       '启停用户',       'API',    2,  '/api/users/{id}/status',      'PUT', NULL,  1, 1, 5,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (8,  'sys:user:reset-password','重置密码',      'API',    2,  '/api/users/{id}/password:reset','PUT', NULL, 1, 1, 6, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (9,  'sys:user:assign-role',  '分配角色',       'API',    2,  '/api/users/{id}/roles',       'PUT', NULL,  1, 1, 7,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (10, 'sys:user:*',            '用户管理全部',   'SYSTEM', 2,  NULL,                          NULL, NULL,   1, 1, 8,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (11, 'sys:role',              '角色管理',       'MENU',   0,  '/system/roles',               NULL, 'team', 1, 1, 20, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (12, 'sys:role:read',         '查询角色',       'API',    11, '/api/roles',                  'GET', NULL,  1, 1, 1,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (13, 'sys:role:create',       '新增角色',       'API',    11, '/api/roles',                  'POST', NULL, 1, 1, 2,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (14, 'sys:role:update',       '编辑角色',       'API',    11, '/api/roles/{id}',             'PUT', NULL,  1, 1, 3,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (15, 'sys:role:delete',       '删除角色',       'API',    11, '/api/roles/{id}',             'DELETE', NULL, 1, 1, 4, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (16, 'sys:role:toggle',       '启停角色',       'API',    11, '/api/roles/{id}/status',      'PUT', NULL,  1, 1, 5,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (17, 'sys:role:assign-perm',  '角色授权',       'API',    11, '/api/roles/{id}/permissions', 'PUT', NULL,  1, 1, 6,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (18, 'sys:role:*',            '角色管理全部',   'SYSTEM', 11, NULL,                          NULL, NULL,   1, 1, 7,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (19, 'sys:perm',              '权限管理',       'MENU',   0,  '/system/permissions',         NULL, 'lock', 1, 1, 30, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (20, 'sys:perm:read',         '查询权限',       'API',    19, '/api/permissions/tree',       'GET', NULL,  1, 1, 1,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (21, 'sys:perm:create',       '新增权限',       'API',    19, '/api/permissions',            'POST', NULL, 1, 1, 2,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (22, 'sys:perm:update',       '编辑权限',       'API',    19, '/api/permissions/{id}',       'PUT', NULL,  1, 1, 3,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (23, 'sys:perm:delete',       '删除权限',       'API',    19, '/api/permissions/{id}',       'DELETE', NULL, 1, 1, 4, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (24, 'sys:perm:*',            '权限管理全部',   'SYSTEM', 19, NULL,                          NULL, NULL,   1, 1, 5,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (25, 'sys:audit',             '审计日志',       'MENU',   0,  '/system/audit-logs',          NULL, 'file-text', 1, 1, 40, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
    (26, 'sys:audit:read',        '查询审计日志',   'API',    25, '/api/audit-logs',             'GET', NULL,  1, 1, 1,  CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT INTO sys_role
    (id, code, name, description, inherit_role_id, status, is_builtin, perm_version, sort, created_by, created_at, updated_by, updated_at)
VALUES
    (1, 'SUPER_ADMIN', '超级管理员', '内置角色：持有全部权限，不可删除或停用', 0, 1, 1, 0, 1, NULL, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3)),
    (2, 'PERM_ADMIN',  '权限管理员', '内置角色：管理用户、角色、权限，只读审计', 0, 1, 1, 0, 2, NULL, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3)),
    (3, 'AUDITOR',     '审计员',     '内置角色：只读审计日志', 0, 1, 1, 0, 3, NULL, CURRENT_TIMESTAMP(3), NULL, CURRENT_TIMESTAMP(3));

INSERT INTO sys_role_permission (role_id, permission_id, created_by, created_at)
VALUES
    (1, 1, NULL, CURRENT_TIMESTAMP(3)),
    (2, 10, NULL, CURRENT_TIMESTAMP(3)),
    (2, 18, NULL, CURRENT_TIMESTAMP(3)),
    (2, 24, NULL, CURRENT_TIMESTAMP(3)),
    (2, 26, NULL, CURRENT_TIMESTAMP(3)),
    (2, 2, NULL, CURRENT_TIMESTAMP(3)),
    (2, 11, NULL, CURRENT_TIMESTAMP(3)),
    (2, 19, NULL, CURRENT_TIMESTAMP(3)),
    (2, 25, NULL, CURRENT_TIMESTAMP(3)),
    (3, 26, NULL, CURRENT_TIMESTAMP(3)),
    (3, 25, NULL, CURRENT_TIMESTAMP(3));
