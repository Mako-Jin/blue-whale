-- =====================================================================
-- Wave 5.87 M2：学习培训考试（whale-lms）培训计划权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-lms-m2.sql
-- 幂等说明 : 权限、菜单、授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-lms M2 培训计划（培训班/场次、必修分配、面授签到、
--            讲师课时台账）。
-- 权限编号 : LMS 段 1900–1949；本脚本使用 1911–1921（M0 已用 1900–1910）。
-- 菜单编号 : LMS 段 450–469；本脚本使用 452（培训计划）。
-- 授权口径 : lms_admin、tenant_admin 获得 1911–1921 全量；
--            lms_lecturer 获得 1911/1915/1919/1921（查看、场次维护、课时台账、完成认定）；
--            lms_member 获得 1911/1917（查看培训班、自助签到）。
-- 前置条件 : bluewhale_lms 已由 Flyway V3 建表；migration-5.87-lms-m0.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1911–1921
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1911, 0, 'sys:lms:training:list', '查询培训班', 'API', '/lms/training/page,/lms/training/{id}', 'GET', 1911, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1911 OR perm_code = 'sys:lms:training:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1912, 0, 'sys:lms:training:create', '新建培训班', 'API', '/lms/training', 'POST', 1912, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1912 OR perm_code = 'sys:lms:training:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1913, 0, 'sys:lms:training:update', '编辑培训班', 'API', '/lms/training/{id}', 'PUT', 1913, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1913 OR perm_code = 'sys:lms:training:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1914, 0, 'sys:lms:training:status', '培训班发布/结课', 'API', '/lms/training/{id}/status', 'PUT', 1914, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1914 OR perm_code = 'sys:lms:training:status');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1915, 0, 'sys:lms:training:session', '场次维护', 'API', '/lms/training/{id}/session,/lms/training/session/{sessionId}', 'POST,DELETE', 1915, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1915 OR perm_code = 'sys:lms:training:session');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1916, 0, 'sys:lms:training:assign', '必修分配', 'API', '/lms/training/{id}/assign', 'POST', 1916, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1916 OR perm_code = 'sys:lms:training:assign');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1917, 0, 'sys:lms:training:checkin', '培训签到', 'API', '/lms/training/session/{sessionId}/checkin', 'POST', 1917, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1917 OR perm_code = 'sys:lms:training:checkin');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1918, 0, 'sys:lms:training:checkin:manage', '补签与签到明细', 'API', '/lms/training/session/{sessionId}/checkin/supplement,/lms/training/{id}/checkin', 'POST,GET', 1918, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1918 OR perm_code = 'sys:lms:training:checkin:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1919, 0, 'sys:lms:training:hours', '讲师课时台账', 'API', '/lms/training/{id}/hours', 'GET', 1919, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1919 OR perm_code = 'sys:lms:training:hours');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1920, 0, 'sys:lms:training:delete', '删除培训班', 'API', '/lms/training/{id}', 'DELETE', 1920, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1920 OR perm_code = 'sys:lms:training:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1921, 0, 'sys:lms:training:completion', '完成认定', 'API', '/lms/training/{id}/completion', 'GET', 1921, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1921 OR perm_code = 'sys:lms:training:completion');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('lms_admin', 'tenant_admin') AND p.id BETWEEN 1911 AND 1921) OR (r.role_code = 'lms_lecturer' AND p.id IN (1911, 1915, 1919, 1921)) OR (r.role_code = 'lms_member' AND p.id IN (1911, 1917)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 452（培训计划，挂在 450 学习培训下）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 452, 1, 450, '培训计划', '/lms/training', 'lms/training/index', 'calendar', 452, 'MENU', 'sys:lms:training:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 452);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1911 AND 1921 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('lms_admin','lms_lecturer','lms_member','tenant_admin')
--   GROUP BY r.role_code;
--   -- 期望：lms_admin/tenant_admin ≥11、lms_lecturer ≥4、lms_member ≥2（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id = 452;
-- =====================================================================
