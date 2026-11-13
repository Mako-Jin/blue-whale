-- ---------------------------------------------------------------------
-- V24 权限中心扩展 M4：用户导入导出
-- 仅新增两个权限点并授予 tenant_admin；不建新表、不加菜单
-- （导入导出挂在既有「用户管理」页面下，无需独立菜单节点）。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 1. 权限点（判重键：perm_code —— 主键撞号不得让整条语句静默跳过）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2413, 0, 'sys:user:import', '导入用户', 'API', '/user/v1/import/*', 'POST', 2413, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:import');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2414, 0, 'sys:user:export', '导出用户', 'API', '/user/v1/export', 'POST', 2414, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:export');

-- ---------------------------------------------------------------------
-- 2. 授予 tenant_admin（按 perm_code 联查）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code IN ('sys:user:import', 'sys:user:export')
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
