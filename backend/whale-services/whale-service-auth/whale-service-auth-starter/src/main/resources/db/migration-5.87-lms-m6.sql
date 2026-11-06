-- =====================================================================
-- Wave 5.87 M6：学习培训考试（whale-lms）学习看板权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-lms-m6.sql
-- 幂等说明 : 权限、菜单、授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-lms M6 学习看板（覆盖率 / 完成率 / 通过率 / 学分分布 + 导出）。
-- 权限编号 : **LMS 原段 1900–1949 已在 M1–M5 用满**（M5 回填 1941–1943 后无空位），
--            故另申请 2000–2019 作为 LMS 分析段，本脚本占用 2000–2002。
--            不挪进 1950–1999：该段已登记为 OA 党建（1950–1969）与工会（1970–1999），
--            借用会让后续 OA 波次无号可用。
-- 菜单编号 : LMS 段 450–469；本脚本使用 460（学习看板）。
-- 授权口径 : 查看（2000）给 lms_admin / tenant_admin / lms_lecturer；
--            刷新（2001）与导出（2002）只给 lms_admin / tenant_admin。
--            讲师需要看自己带班的完成情况，但不该能触发全量快照落库、
--            也不该能把全院数据导成文件带走。
-- 设计说明 : 看板是聚合口径（无个人信息），导出仍单独设权：
--            聚合数据本身不敏感，但"能批量导出"是一种能力，应当可审计、可控。
-- 前置条件 : bluewhale_lms 已由 Flyway V9 建表；migration-5.87-lms-m5a.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 2000–2002
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2000, 0, 'sys:lms:analytics:view', '查看学习看板', 'API', '/lms/analytics/overview,/lms/analytics/snapshot/**', 'GET', 2000, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2000 OR perm_code = 'sys:lms:analytics:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2001, 0, 'sys:lms:analytics:refresh', '刷新学习看板快照', 'API', '/lms/analytics/refresh', 'POST', 2001, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2001 OR perm_code = 'sys:lms:analytics:refresh');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2002, 0, 'sys:lms:analytics:export', '导出学习看板数据', 'API', '/lms/analytics/export', 'GET', 2002, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2002 OR perm_code = 'sys:lms:analytics:export');

-- ---------------------------------------------------------------------
-- 2. 授权：查看给三角色，刷新与导出只给管理员
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('lms_admin', 'tenant_admin', 'lms_lecturer') AND p.id = 2000 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('lms_admin', 'tenant_admin') AND p.id BETWEEN 2001 AND 2002 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 460（学习看板），挂在 450 学习培训下
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 460, 1, 450, '学习看板', '/lms/analytics', 'lms/analytics/index', 'chart', 460, 'MENU', 'sys:lms:analytics:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 460);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 2000 AND 2002 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('lms_admin','lms_lecturer','lms_member','tenant_admin')
--   GROUP BY r.role_code;
--   -- 期望：lms_admin/tenant_admin 各 +3，lms_lecturer +1，lms_member 不变。
--   SELECT id, name, path FROM sys_menu WHERE id = 460;
-- =====================================================================
