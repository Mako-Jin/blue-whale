-- =====================================================================
-- Wave 5.82 M1A-1：OA 审批中心权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m1a-approval-center.sql
-- 幂等说明 : 权限、角色授权与菜单均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增审批中心（我发起/待我审批/我已审批/抄送我的）、
--            审批详情与动作、催办/撤销、代理设置与流程监控。
-- 权限编号 : OA 段 1600–1699；本脚本使用 1642–1647，任务动作复用 1640。
-- 菜单编号 : OA 段 360–399；本脚本使用 360–362。
-- 授权口径 : oa_admin / oa_hr_admin / tenant_admin 获得 1642–1647；
--            oa_manager 获得 1642/1643/1644/1646/1647；
--            oa_member 获得 1642/1643/1647；
--            1640（审批操作）已在 5.82-oa-m1a 脚本中授予上述角色，本脚本不重复授予。
-- 前置条件 : bluewhale_oa 已执行 V4__oa_m1a_leave.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1642–1647
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1642, 0, 'sys:oa:approval:list', '查询审批中心', 'API', '/oa/approval/center/initiated/page,/oa/approval/center/pending/page,/oa/approval/center/approved/page,/oa/approval/center/cc/page', 'GET', 1642, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1642 OR perm_code = 'sys:oa:approval:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1643, 0, 'sys:oa:approval:view', '查看审批详情', 'API', '/oa/approval/center/instance/{workflowInstanceId}', 'GET', 1643, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1643 OR perm_code = 'sys:oa:approval:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1644, 0, 'sys:oa:approval:urge', '催办审批', 'API', '/oa/approval/center/instance/{workflowInstanceId}/urge', 'POST', 1644, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1644 OR perm_code = 'sys:oa:approval:urge');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1645, 0, 'sys:oa:approval:delegate', '审批代理设置', 'API', '/oa/approval/delegate/page,/oa/approval/delegate,/oa/approval/delegate/{id}', 'ALL', 1645, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1645 OR perm_code = 'sys:oa:approval:delegate');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1646, 0, 'sys:oa:approval:monitor', '审批流程监控', 'API', '/oa/approval/monitor/page,/oa/approval/monitor/instance/{workflowInstanceId}', 'GET', 1646, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1646 OR perm_code = 'sys:oa:approval:monitor');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1647, 0, 'sys:oa:approval:cancel', '撤销审批', 'API', '/oa/approval/center/instance/{workflowInstanceId}/cancel', 'POST', 1647, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1647 OR perm_code = 'sys:oa:approval:cancel');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_hr_admin', 'tenant_admin') AND p.id BETWEEN 1642 AND 1647) OR (r.role_code = 'oa_manager' AND p.id IN (1642, 1643, 1644, 1646, 1647)) OR (r.role_code = 'oa_member' AND p.id IN (1642, 1643, 1647)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 360–362（OA 根菜单尚未落地，暂挂顶级 parent_id = 0）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 360, 1, 0, '审批中心', '/oa/approval', 'oa/approval/index', 'audit', 360, 'MENU', 'sys:oa:approval:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 360);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 361, 1, 0, '代理设置', '/oa/approval/delegate', 'oa/approval/delegate/index', 'swap', 361, 'MENU', 'sys:oa:approval:delegate', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 361);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 362, 1, 0, '流程监控', '/oa/approval/monitor', 'oa/approval/monitor/index', 'monitor', 362, 'MENU', 'sys:oa:approval:monitor', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 362);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1642 AND 1647 ORDER BY id;
--   -- 期望：6 行，1642..1647 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('oa_admin','oa_hr_admin','oa_manager','oa_member')
--     AND rp.permission_id BETWEEN 1642 AND 1647
--   GROUP BY r.role_code;
--   -- 期望：oa_admin=6、oa_hr_admin=6、oa_manager=5、oa_member=3。
--   -- 3) 菜单登记
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 360 AND 362 ORDER BY id;
--   -- 期望：3 行。
-- =====================================================================
