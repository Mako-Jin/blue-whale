-- =====================================================================
-- migration-5.82-oa-approval-perm-seed.sql
-- 用途：补登 OA 审批业务配置 / 审批人规则 / 实例登记与结果同步 6 个权限码。
-- 背景：2026-11-08 工作区扫描实锤——控制器 @RequirePermission 已引用，
--       但 sys_permission 无对应行；网关按 DB 全量权限生成
--       X-Permission-Codes，缺行导致连超管访问 /oa/approval/config/page
--       都返回 403。
-- 号段：1648–1653（1640–1647 已被 M1A 占用，1660 起已被后续波次占用）。
-- 幂等：全部为「不存在才插入」，可重复执行。
-- 注意：sys_permission 无 tenant_id 列（勿写）；导入必须带
--       --default-character-set=utf8mb4，否则中文权限名写坏。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点登记（parent_id = 0，与 M1A 的 1640–1647 同层）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1648, 0, 'sys:oa:approval:config:list', '查询审批配置', 'API', '/oa/approval/config/page,/oa/approval/config/{id}', 'GET', 1648, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1648 OR perm_code = 'sys:oa:approval:config:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1649, 0, 'sys:oa:approval:config:edit', '编辑审批配置', 'API', '/oa/approval/config,/oa/approval/config/{id},/oa/approval/config/{id}/enabled', 'POST/PUT', 1649, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1649 OR perm_code = 'sys:oa:approval:config:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1650, 0, 'sys:oa:approval:rule:list', '查询审批人规则', 'API', '/oa/approval/route-rule/list', 'GET', 1650, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1650 OR perm_code = 'sys:oa:approval:rule:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1651, 0, 'sys:oa:approval:rule:edit', '编辑审批人规则', 'API', '/oa/approval/route-rule,/oa/approval/route-rule/{id}', 'POST/DELETE', 1651, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1651 OR perm_code = 'sys:oa:approval:rule:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1652, 0, 'sys:oa:approval:register', '登记审批实例', 'API', '/oa/approval/instance,/oa/approval/instance/{id}/workflow', 'POST/PUT', 1652, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1652 OR perm_code = 'sys:oa:approval:register');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1653, 0, 'sys:oa:approval:result', '同步审批结果', 'API', '/oa/approval/instance/{id}/result', 'PUT', 1653, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1653 OR perm_code = 'sys:oa:approval:result');

-- ---------------------------------------------------------------------
-- 2. 授权（仅管理员类角色：配置/规则属管理端，登记/结果同步属业务后端调用）
--    按角色编码匹配，覆盖平台与既有业务租户；ID 由角色+权限键 MD5 派生，幂等。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_hr_admin', 'tenant_admin') AND p.id BETWEEN 1648 AND 1653) OR (r.role_code = 'oa_manager' AND p.id IN (1650, 1652, 1653)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1648 AND 1653 ORDER BY id;
--   -- 期望：6 行，1648..1653 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('oa_admin','oa_hr_admin','oa_manager','tenant_admin')
--     AND rp.permission_id BETWEEN 1648 AND 1653
--   GROUP BY r.role_code;
--   -- 期望：oa_admin=6、oa_hr_admin=6、tenant_admin=6、oa_manager=3。
-- =====================================================================
