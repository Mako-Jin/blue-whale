-- =====================================================================
-- Wave 5.82 M2-5a：OA 考勤 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m2-attendance.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增考勤打卡、日报重算、主数据（组/班次/规则/地点/设备）、
--            异常处理与补卡申请。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1680–1689。
-- 菜单编号 : OA 段 360–409；本脚本使用 373–376。
-- 授权口径 : oa_admin、oa_hr_admin、tenant_admin 获得 1680–1689 全量；
--            oa_manager 获得查询/打卡/重算/配置（不含删除）；
--            oa_member 仅获得 1680/1681/1682/1689（打卡、看日报、提补卡）。
-- 前置条件 : bluewhale_oa 已执行 V7__attendance.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1680–1689
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1680, 0, 'sys:oa:attendance:record:list', '查询打卡记录', 'API', '/oa/attendance/records/page', 'GET', 1680, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1680 OR perm_code = 'sys:oa:attendance:record:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1681, 0, 'sys:oa:attendance:record:add', '考勤打卡', 'API', '/oa/attendance/records', 'POST', 1681, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1681 OR perm_code = 'sys:oa:attendance:record:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1682, 0, 'sys:oa:attendance:daily:list', '查询考勤日报', 'API', '/oa/attendance/daily/page', 'GET', 1682, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1682 OR perm_code = 'sys:oa:attendance:daily:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1683, 0, 'sys:oa:attendance:daily:recalculate', '重算考勤日报', 'API', '/oa/attendance/daily/recalculate', 'POST', 1683, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1683 OR perm_code = 'sys:oa:attendance:daily:recalculate');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1684, 0, 'sys:oa:attendance:config:list', '查询考勤主数据', 'API',
       '/oa/attendance/groups,/oa/attendance/shifts,/oa/attendance/rules,/oa/attendance/locations,/oa/attendance/devices,/oa/attendance/exceptions/page,/oa/attendance/corrections/page',
       'GET', 1684, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1684 OR perm_code = 'sys:oa:attendance:config:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1685, 0, 'sys:oa:attendance:config:add', '新增考勤主数据', 'API',
       '/oa/attendance/groups,/oa/attendance/shifts,/oa/attendance/rules,/oa/attendance/locations,/oa/attendance/devices',
       'POST', 1685, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1685 OR perm_code = 'sys:oa:attendance:config:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1686, 0, 'sys:oa:attendance:config:edit', '编辑考勤主数据', 'API',
       '/oa/attendance/groups/{id},/oa/attendance/shifts/{id},/oa/attendance/rules/{id},/oa/attendance/locations/{id},/oa/attendance/devices/{id}',
       'PUT', 1686, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1686 OR perm_code = 'sys:oa:attendance:config:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1687, 0, 'sys:oa:attendance:config:delete', '删除考勤主数据', 'API',
       '/oa/attendance/groups/{id},/oa/attendance/shifts/{id},/oa/attendance/rules/{id},/oa/attendance/locations/{id},/oa/attendance/devices/{id}',
       'DELETE', 1687, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1687 OR perm_code = 'sys:oa:attendance:config:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1688, 0, 'sys:oa:attendance:exception:handle', '处理考勤异常与补卡', 'API',
       '/oa/attendance/exceptions/{id}/status,/oa/attendance/corrections/{id}/status', 'PUT', 1688, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1688 OR perm_code = 'sys:oa:attendance:exception:handle');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1689, 0, 'sys:oa:attendance:correction:edit', '提交补卡申请', 'API', '/oa/attendance/corrections', 'POST', 1689, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1689 OR perm_code = 'sys:oa:attendance:correction:edit');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_hr_admin', 'tenant_admin') AND p.id BETWEEN 1680 AND 1689) OR (r.role_code = 'oa_manager' AND p.id IN (1680, 1681, 1682, 1683, 1684, 1685, 1686, 1688, 1689)) OR (r.role_code = 'oa_member' AND p.id IN (1680, 1681, 1682, 1689)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 373–376
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 373, 1, 0, '考勤打卡', '/oa/attendance/punch', 'oa/attendance/punch/index', 'login', 373, 'MENU', 'sys:oa:attendance:record:add', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 373);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 374, 1, 0, '考勤日报', '/oa/attendance/daily', 'oa/attendance/daily/index', 'calendar', 374, 'MENU', 'sys:oa:attendance:daily:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 374);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 375, 1, 0, '考勤规则', '/oa/attendance/rules', 'oa/attendance/rules/index', 'setting', 375, 'MENU', 'sys:oa:attendance:config:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 375);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 376, 1, 0, '补卡与异常', '/oa/attendance/correction', 'oa/attendance/correction/index', 'exception', 376, 'MENU', 'sys:oa:attendance:correction:edit', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 376);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1680 AND 1689 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 373 AND 376;
-- =====================================================================
