-- =====================================================================
-- Wave 5.82 M1B-2：OA 出差申请 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m1b-trip.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增出差申请（CRUD、状态变更、审批结果回写、预支生成、报告提交）。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1664–1668。
-- 菜单编号 : OA 段 360–409；本脚本使用 371。
-- 授权口径 : oa_admin、oa_hr_admin、tenant_admin 获得 1664–1668 全量；
--            oa_manager / oa_member 获得 1664/1665/1666/1667（可查询、提交、撤销、报告）。
-- 前置条件 : bluewhale_oa 已执行 V6__oa_m1b_trip.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1664–1668
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1664, 0, 'sys:oa:trip:list', '查询出差申请', 'API', '/oa/trip/page,/oa/trip/{id}', 'GET', 1664, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1664 OR perm_code = 'sys:oa:trip:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1665, 0, 'sys:oa:trip:add', '提交出差申请', 'API', '/oa/trip', 'POST', 1665, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1665 OR perm_code = 'sys:oa:trip:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1666, 0, 'sys:oa:trip:edit', '维护出差申请', 'API', '/oa/trip/{id},/oa/trip/{id}/status', 'PUT', 1666, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1666 OR perm_code = 'sys:oa:trip:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1667, 0, 'sys:oa:trip:report', '提交出差报告', 'API', '/oa/trip/{id}/report', 'POST', 1667, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1667 OR perm_code = 'sys:oa:trip:report');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1668, 0, 'sys:oa:trip:delete', '删除出差申请', 'API', '/oa/trip/{id}', 'DELETE', 1668, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1668 OR perm_code = 'sys:oa:trip:delete');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_hr_admin', 'tenant_admin') AND p.id BETWEEN 1664 AND 1668) OR (r.role_code IN ('oa_manager', 'oa_member') AND p.id IN (1664, 1665, 1666, 1667)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 371（OA 根菜单尚未落地，暂挂顶级 parent_id = 0）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 371, 1, 0, '出差申请', '/oa/hr/trip', 'oa/hr/trip/index', 'car', 371, 'MENU', 'sys:oa:trip:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 371);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1664 AND 1668 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('oa_admin','oa_hr_admin','oa_manager','oa_member')
--   GROUP BY r.role_code;
--   -- 期望：oa_admin/oa_hr_admin 获得 5 项、oa_manager/oa_member 获得 4 项（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id = 371;
-- =====================================================================
