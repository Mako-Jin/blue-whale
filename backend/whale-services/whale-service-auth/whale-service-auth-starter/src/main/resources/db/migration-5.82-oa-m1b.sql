-- =====================================================================
-- Wave 5.82 M1B：OA 加班申请 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m1b.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增加班申请（CRUD、状态变更、审批结果回写、调休入账）。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1660–1663。
-- 菜单编号 : OA 段 360–409；本脚本使用 370。
-- 授权口径 : oa_admin、oa_hr_admin、tenant_admin 获得 1660–1663 全量；
--            oa_manager / oa_member 获得 1660/1661/1662（可查询、提交、撤销）。
-- 前置条件 : bluewhale_oa 已执行 V5__oa_m1b_overtime.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1660–1663
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1660, 0, 'sys:oa:overtime:list', '查询加班申请', 'API', '/oa/overtime/page,/oa/overtime/{id}', 'GET', 1660, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1660 OR perm_code = 'sys:oa:overtime:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1661, 0, 'sys:oa:overtime:add', '提交加班申请', 'API', '/oa/overtime', 'POST', 1661, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1661 OR perm_code = 'sys:oa:overtime:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1662, 0, 'sys:oa:overtime:edit', '维护加班申请', 'API', '/oa/overtime/{id},/oa/overtime/{id}/status', 'PUT', 1662, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1662 OR perm_code = 'sys:oa:overtime:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1663, 0, 'sys:oa:overtime:delete', '删除加班申请', 'API', '/oa/overtime/{id}', 'DELETE', 1663, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1663 OR perm_code = 'sys:oa:overtime:delete');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_hr_admin', 'tenant_admin') AND p.id BETWEEN 1660 AND 1663) OR (r.role_code IN ('oa_manager', 'oa_member') AND p.id IN (1660, 1661, 1662)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 370（OA 根菜单尚未落地，暂挂顶级 parent_id = 0）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 370, 1, 0, '加班申请', '/oa/hr/overtime', 'oa/hr/overtime/index', 'clock-circle', 370, 'MENU', 'sys:oa:overtime:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 370);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1660 AND 1663 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('oa_admin','oa_hr_admin','oa_manager','oa_member')
--   GROUP BY r.role_code;
--   -- 期望：oa_admin/oa_hr_admin ≥4、oa_manager/oa_member ≥3（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id = 370;
-- =====================================================================
