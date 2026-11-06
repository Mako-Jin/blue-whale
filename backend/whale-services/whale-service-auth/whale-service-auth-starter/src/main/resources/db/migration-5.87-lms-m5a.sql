-- =====================================================================
-- Wave 5.87 M5-1：学习培训考试（whale-lms）集成收口权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-lms-m5a.sql
-- 幂等说明 : 权限、菜单、授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-lms M5-1 集成底座（ERP 员工只读快照 + 通知投递留痕）。
-- 权限编号 : LMS 段 1900–1949；M3 用 1922–1940，M4 用 1944–1949，
--            本脚本回填此前跳空的 1941–1943，LMS 段至此真正用满。
-- 菜单编号 : LMS 段 450–469；本脚本使用 459（集成设置）。
-- 授权口径 : 仅 lms_admin / tenant_admin 获得 1941–1943。
--            员工快照含组织与在职状态、通知日志含全员触达记录，
--            属于运维视角数据，讲师与学员一律不授。
-- 设计说明 : 同步（1941）、日志查看（1942）、催办执行（1943）三权分设——
--            能看日志的人不必然能触发全量同步，能催办的人也不必然能改员工快照。
-- 前置条件 : bluewhale_lms 已由 Flyway V8 建表；migration-5.87-lms-m4b.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1941–1943
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1941, 0, 'sys:lms:member:manage', '同步与查询员工快照', 'API', '/lms/integration/member/**', 'GET,POST', 1941, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1941 OR perm_code = 'sys:lms:member:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1942, 0, 'sys:lms:notify:list', '查看通知投递日志', 'API', '/lms/integration/notify/**', 'GET', 1942, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1942 OR perm_code = 'sys:lms:notify:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1943, 0, 'sys:lms:urge:manage', '执行未完成必修催办', 'API', '/lms/urge/**', 'GET,POST', 1943, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1943 OR perm_code = 'sys:lms:urge:manage');

-- ---------------------------------------------------------------------
-- 2. 授权（仅管理员角色）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('lms_admin', 'tenant_admin') AND p.id BETWEEN 1941 AND 1943 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 459（集成设置），挂在 450 学习培训下
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 459, 1, 450, '集成设置', '/lms/integration', 'lms/integration/index', 'sync', 459, 'MENU', 'sys:lms:member:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 459);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1941 AND 1943 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('lms_admin','lms_lecturer','lms_member','tenant_admin')
--   GROUP BY r.role_code;
--   -- 期望：lms_admin/tenant_admin 各 +3（累计 ≥3），lms_lecturer 与 lms_member 不变。
--   SELECT id, name, path FROM sys_menu WHERE id = 459;
-- =====================================================================
