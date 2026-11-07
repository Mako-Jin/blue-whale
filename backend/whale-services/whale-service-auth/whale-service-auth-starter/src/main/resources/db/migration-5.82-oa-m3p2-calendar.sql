-- =====================================================================
-- Wave 5.82 M3-P2：OA 日程 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m3p2-calendar.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增个人/共享/公开日程与即将提醒查询。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1740–1742。
-- 菜单编号 : OA 段 360–409；本脚本使用 386。
-- 授权口径 : oa_admin、oa_manager、tenant_admin 与 oa_member 均获得 1740–1742
--            （日程为全员个人能力）。
-- 前置条件 : bluewhale_oa 已执行 V10__calendar.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1740–1742
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1740, 0, 'sys:oa:calendar:list', '查询日程', 'API',
       '/oa/calendar/page,/oa/calendar/{id}', 'GET', 1740, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1740 OR perm_code = 'sys:oa:calendar:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1741, 0, 'sys:oa:calendar:manage', '管理日程与共享', 'API',
       '/oa/calendar,/oa/calendar/{id},/oa/calendar/{id}/shares', 'POST,PUT,DELETE', 1741, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1741 OR perm_code = 'sys:oa:calendar:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1742, 0, 'sys:oa:calendar:reminder', '日程提醒查询', 'API',
       '/oa/calendar/reminders', 'GET', 1742, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1742 OR perm_code = 'sys:oa:calendar:reminder');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_manager', 'tenant_admin', 'oa_member') AND p.id BETWEEN 1740 AND 1742) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 386
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 386, 1, 0, '日程管理', '/oa/collab/calendar', 'collab/calendar/index', 'calendar', 386, 'MENU', 'sys:oa:calendar:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 386);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1740 AND 1742 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id = 386;
-- =====================================================================
