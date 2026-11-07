-- =====================================================================
-- Wave 5.82 M2-5c：OA 用车 / 用章 / 采购申请 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m2-admin.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增用车预约、用章申请、采购申请，用章校验 Contract 印章授权，
--            采购审批通过后推送 ERP 采购订单草稿。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1700–1717。
-- 菜单编号 : OA 段 360–409；本脚本使用 380–382。
-- 授权口径 : oa_admin、oa_hr_admin、oa_finance、tenant_admin 获得 1700–1717 全量；
--            oa_manager 获得 1700、1704–1710、1713–1715；
--            oa_member 获得 1700、1704、1708、1709、1713、1714。
-- 前置条件 : bluewhale_oa 已执行 V8__expense_admin.sql（行政 8 张表）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1700–1717
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1700, 0, 'sys:oa:vehicle:list', '查询车辆与用车单', 'API',
       '/oa/vehicle/page,/oa/vehicle/{id},/oa/vehicle/drivers/page,/oa/vehicle/bookings/page,/oa/vehicle/bookings/{id}',
       'GET', 1700, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1700 OR perm_code = 'sys:oa:vehicle:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1701, 0, 'sys:oa:vehicle:add', '新增车辆与司机', 'API',
       '/oa/vehicle,/oa/vehicle/drivers', 'POST', 1701, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1701 OR perm_code = 'sys:oa:vehicle:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1702, 0, 'sys:oa:vehicle:edit', '编辑车辆与司机', 'API',
       '/oa/vehicle/{id},/oa/vehicle/drivers/{id}', 'PUT', 1702, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1702 OR perm_code = 'sys:oa:vehicle:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1703, 0, 'sys:oa:vehicle:delete', '删除车辆与司机', 'API',
       '/oa/vehicle/{id},/oa/vehicle/drivers/{id}', 'DELETE', 1703, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1703 OR perm_code = 'sys:oa:vehicle:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1704, 0, 'sys:oa:vehicle:booking:add', '新建用车申请', 'API',
       '/oa/vehicle/bookings', 'POST', 1704, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1704 OR perm_code = 'sys:oa:vehicle:booking:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1705, 0, 'sys:oa:vehicle:booking:edit', '编辑与撤销用车申请', 'API',
       '/oa/vehicle/bookings/{id},/oa/vehicle/bookings/{id}/status', 'PUT', 1705, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1705 OR perm_code = 'sys:oa:vehicle:booking:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1706, 0, 'sys:oa:vehicle:dispatch', '派车', 'API',
       '/oa/vehicle/bookings/{id}/dispatch', 'POST', 1706, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1706 OR perm_code = 'sys:oa:vehicle:dispatch');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1707, 0, 'sys:oa:vehicle:return', '回车登记', 'API',
       '/oa/vehicle/bookings/{id}/return', 'POST', 1707, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1707 OR perm_code = 'sys:oa:vehicle:return');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1708, 0, 'sys:oa:seal:list', '查询用章申请', 'API',
       '/oa/seal/page,/oa/seal/{id},/oa/seal/available-seals', 'GET', 1708, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1708 OR perm_code = 'sys:oa:seal:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1709, 0, 'sys:oa:seal:add', '新建用章申请', 'API',
       '/oa/seal', 'POST', 1709, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1709 OR perm_code = 'sys:oa:seal:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1710, 0, 'sys:oa:seal:edit', '编辑与撤销用章申请', 'API',
       '/oa/seal/{id},/oa/seal/{id}/status', 'PUT', 1710, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1710 OR perm_code = 'sys:oa:seal:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1711, 0, 'sys:oa:seal:delete', '删除用章申请', 'API',
       '/oa/seal/{id}', 'DELETE', 1711, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1711 OR perm_code = 'sys:oa:seal:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1712, 0, 'sys:oa:seal:register', '用印登记', 'API',
       '/oa/seal/{id}/register', 'POST', 1712, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1712 OR perm_code = 'sys:oa:seal:register');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1713, 0, 'sys:oa:purchase:list', '查询采购申请', 'API',
       '/oa/purchase/page,/oa/purchase/{id}', 'GET', 1713, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1713 OR perm_code = 'sys:oa:purchase:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1714, 0, 'sys:oa:purchase:add', '新建采购申请', 'API',
       '/oa/purchase', 'POST', 1714, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1714 OR perm_code = 'sys:oa:purchase:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1715, 0, 'sys:oa:purchase:edit', '编辑与撤销采购申请', 'API',
       '/oa/purchase/{id},/oa/purchase/{id}/status', 'PUT', 1715, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1715 OR perm_code = 'sys:oa:purchase:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1716, 0, 'sys:oa:purchase:delete', '删除采购申请', 'API',
       '/oa/purchase/{id}', 'DELETE', 1716, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1716 OR perm_code = 'sys:oa:purchase:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1717, 0, 'sys:oa:purchase:push', '重试推送采购订单', 'API',
       '/oa/purchase/{id}/push-retry', 'POST', 1717, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1717 OR perm_code = 'sys:oa:purchase:push');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_hr_admin', 'oa_finance', 'tenant_admin') AND p.id BETWEEN 1700 AND 1717) OR (r.role_code = 'oa_manager' AND p.id IN (1700, 1704, 1705, 1706, 1707, 1708, 1709, 1710, 1713, 1714, 1715)) OR (r.role_code = 'oa_member' AND p.id IN (1700, 1704, 1708, 1709, 1713, 1714)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 380–382（OA 根菜单尚未落地，暂挂顶级 parent_id = 0）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 380, 1, 0, '用车管理', '/oa/vehicle/list', 'oa/vehicle/list/index', 'car', 380, 'MENU', 'sys:oa:vehicle:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 380);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 381, 1, 0, '用章申请', '/oa/seal/list', 'oa/seal/list/index', 'stamp', 381, 'MENU', 'sys:oa:seal:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 381);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 382, 1, 0, '采购申请', '/oa/purchase/list', 'oa/purchase/list/index', 'shopping-cart', 382, 'MENU', 'sys:oa:purchase:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 382);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1700 AND 1717 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('oa_admin','oa_hr_admin','oa_finance','oa_manager','oa_member')
--   GROUP BY r.role_code;
--   -- 期望：oa_admin/oa_hr_admin/oa_finance ≥18、oa_manager ≥11、oa_member ≥6（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 380 AND 382;
-- =====================================================================
