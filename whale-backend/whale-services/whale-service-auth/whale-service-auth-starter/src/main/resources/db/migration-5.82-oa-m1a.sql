-- =====================================================================
-- Wave 5.82 M1A：OA 请假闭环 / 员工档案 / 额度 / 日历 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m1a.sql
-- 幂等说明 : 权限、角色、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增员工扩展档案、假期类型、额度台账、工作日历，
--            并补齐请假提交/撤销与审批动作/抄送权限位。
-- 权限编号 : OA 段 1600–1699；本脚本使用 1630–1641（1642 起预留）。
-- 菜单编号 : OA 段 360–399；本脚本使用 365–369。
-- 角色编号 : 平台模板 25122 oa_admin / 25123 oa_hr_admin；oa_manager(16801) 与
--            oa_member(16802) 已在 5.65 脚本创建，仅按角色编码补授权。
--            业务租户按 5.65 口径复制 oa_admin / oa_hr_admin（已存在则跳过）。
-- 授权口径 : oa_admin、oa_hr_admin、tenant_admin 获得 1630–1641 全量；
--            oa_manager 获得 1630/1632/1634/1636/1638/1639/1640；
--            oa_member 获得 1638/1639/1640。
-- 前置条件 : bluewhale_oa 已执行 V4__oa_m1a_leave.sql。
-- 备注     : 仓库内尚无 OA 根菜单（M0-7 未落地），菜单以 parent_id = 0 挂为顶级，
--            待 OA 根菜单创建后可再迁移挂载；不影响权限码与授权。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1630–1641
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1630, 0, 'sys:oa:hr:profile:list', '查询员工档案', 'API', '/oa/hr/profile/page,/oa/hr/profile/{userId}', 'GET', 1630, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1630 OR perm_code = 'sys:oa:hr:profile:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1631, 0, 'sys:oa:hr:profile:edit', '维护员工档案', 'API', '/oa/hr/profile,/oa/hr/profile/{userId}', 'POST/DELETE', 1631, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1631 OR perm_code = 'sys:oa:hr:profile:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1632, 0, 'sys:oa:hr:leave-type:list', '查询假期类型', 'API', '/oa/hr/leave-type/page,/oa/hr/leave-type/enabled', 'GET', 1632, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1632 OR perm_code = 'sys:oa:hr:leave-type:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1633, 0, 'sys:oa:hr:leave-type:edit', '维护假期类型', 'API', '/oa/hr/leave-type,/oa/hr/leave-type/{id},/oa/hr/leave-type/{id}/toggle', 'POST/PUT', 1633, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1633 OR perm_code = 'sys:oa:hr:leave-type:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1634, 0, 'sys:oa:hr:balance:list', '查询假期额度', 'API', '/oa/hr/leave-balance/page,/oa/hr/leave-balance/my,/oa/hr/leave-balance/{id}/logs', 'GET', 1634, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1634 OR perm_code = 'sys:oa:hr:balance:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1635, 0, 'sys:oa:hr:balance:edit', '维护假期额度', 'API', '/oa/hr/leave-balance/init,/oa/hr/leave-balance/{id}', 'POST/PUT', 1635, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1635 OR perm_code = 'sys:oa:hr:balance:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1636, 0, 'sys:oa:hr:calendar:list', '查询工作日历', 'API', '/oa/hr/calendar/page', 'GET', 1636, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1636 OR perm_code = 'sys:oa:hr:calendar:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1637, 0, 'sys:oa:hr:calendar:edit', '维护工作日历', 'API', '/oa/hr/calendar/{date}', 'PUT', 1637, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1637 OR perm_code = 'sys:oa:hr:calendar:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1638, 0, 'sys:oa:leave:submit', '提交请假申请', 'API', '/oa/leave', 'POST', 1638, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1638 OR perm_code = 'sys:oa:leave:submit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1639, 0, 'sys:oa:leave:cancel', '撤销请假申请', 'API', '/oa/leave/{id}/status,/oa/leave/{id}', 'PUT/DELETE', 1639, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1639 OR perm_code = 'sys:oa:leave:cancel');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1640, 0, 'sys:oa:approval:action', '审批操作', 'API', '/oa/approval/instance/{id}/result,/oa/approval/instance/{id}/status', 'PUT/POST', 1640, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1640 OR perm_code = 'sys:oa:approval:action');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1641, 0, 'sys:oa:approval:cc', '审批抄送', 'API', '/oa/approval/instance/{id}/cc,/oa/approval/instance/{id}/cc/{userId}', 'POST/DELETE', 1641, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1641 OR perm_code = 'sys:oa:approval:cc');

-- ---------------------------------------------------------------------
-- 2. 角色模板（平台租户 tenant_id = 1；仓库内尚无 M0 角色定义时补齐，已存在则跳过）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25122, 1, 'oa_admin', 'OA 管理员', 'OA 全部功能与配置', 251, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25122 OR (tenant_id = 1 AND role_code = 'oa_admin'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25123, 1, 'oa_hr_admin', 'OA 人事管理员', '员工档案/假期类型/额度台账/工作日历', 252, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25123 OR (tenant_id = 1 AND role_code = 'oa_hr_admin'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':oa_admin')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'oa_admin' AS role_code, 'OA 管理员' AS role_name, 'OA 全部功能与配置' AS description, 251 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':oa_hr_admin')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'oa_hr_admin' AS role_code, 'OA 人事管理员' AS role_name, '员工档案/假期类型/额度台账/工作日历' AS description, 252 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_hr_admin', 'tenant_admin') AND p.id BETWEEN 1630 AND 1641) OR (r.role_code = 'oa_manager' AND p.id IN (1630, 1632, 1634, 1636, 1638, 1639, 1640)) OR (r.role_code = 'oa_member' AND p.id IN (1638, 1639, 1640)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单 365–369（OA 根菜单尚未落地，暂挂顶级 parent_id = 0）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 365, 1, 0, '请假与额度', '/oa/hr/leave', 'oa/hr/leave/index', 'calendar', 365, 'MENU', 'sys:oa:leave:submit', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 365);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 366, 1, 0, '假期类型', '/oa/hr/leave-type', 'oa/hr/leave-type/index', 'setting', 366, 'MENU', 'sys:oa:hr:leave-type:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 366);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 367, 1, 0, '额度台账', '/oa/hr/leave-balance', 'oa/hr/leave-balance/index', 'account', 367, 'MENU', 'sys:oa:hr:balance:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 367);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 368, 1, 0, '员工档案', '/oa/hr/profile', 'oa/hr/profile/index', 'user', 368, 'MENU', 'sys:oa:hr:profile:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 368);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 369, 1, 0, '工作日历', '/oa/hr/calendar', 'oa/hr/calendar/index', 'date', 369, 'MENU', 'sys:oa:hr:calendar:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 369);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1630 AND 1641 ORDER BY id;
--   -- 期望：12 行，1630..1641 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('oa_admin','oa_hr_admin','oa_manager','oa_member')
--   GROUP BY r.role_code;
--   -- 期望：oa_admin=12、oa_hr_admin=12、oa_manager=7、oa_member=3。
--   -- 3) 菜单登记
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 365 AND 369 ORDER BY id;
--   -- 期望：5 行。
-- =====================================================================
