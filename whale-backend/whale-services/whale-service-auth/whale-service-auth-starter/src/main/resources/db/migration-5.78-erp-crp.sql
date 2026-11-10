-- =====================================================================
-- Wave 5.78 M3 剩余：ERP CRP 权限/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-crp.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp MFG 上下文新增工作中心、工艺路线快照与 CRP 产能负荷；
--            本脚本登记权限点、授权与菜单。
-- 权限编号 : 已用至 544（MRP），本脚本使用 545–550；551–569 预留。
-- 菜单编号 : 已用至 105（计划订单），本脚本使用 106–107；108 预留。
-- 角色     : 沿用 25113 erp_mfg，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_mfg 获得 545–550；
--            erp_member 仅获得查询 545/549；erp_finance 不授予。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 工作中心与 CRP 权限 545–550
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 545, 0, 'sys:erp:mfg:workcenter:list', '查询工作中心', 'API', '/erp/mfg/work-centers/page,/erp/mfg/work-centers/all', 'GET', 545, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 545 OR perm_code = 'sys:erp:mfg:workcenter:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 546, 0, 'sys:erp:mfg:workcenter:add', '新增工作中心', 'API', '/erp/mfg/work-centers', 'POST', 546, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 546 OR perm_code = 'sys:erp:mfg:workcenter:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 547, 0, 'sys:erp:mfg:workcenter:edit', '编辑工作中心', 'API', '/erp/mfg/work-centers/{id}', 'PUT', 547, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 547 OR perm_code = 'sys:erp:mfg:workcenter:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 548, 0, 'sys:erp:mfg:workcenter:delete', '删除工作中心', 'API', '/erp/mfg/work-centers/{id}', 'DELETE', 548, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 548 OR perm_code = 'sys:erp:mfg:workcenter:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 549, 0, 'sys:erp:mfg:crp:view', '查询产能负荷与排程', 'API', '/erp/mfg/crp/load', 'GET', 549, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 549 OR perm_code = 'sys:erp:mfg:crp:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 550, 0, 'sys:erp:production:operation:sync', '同步生产订单工艺路线', 'API', '/erp/mfg/production/{id}/routing/sync', 'POST', 550, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 550 OR perm_code = 'sys:erp:production:operation:sync');

-- ---------------------------------------------------------------------
-- 2. 授权
--    tenant_admin / erp_manager / erp_mfg：545–550
--    erp_member：545/549（只读）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 545 AND 550 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_mfg')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (545, 549) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 106–107（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 106, 1, 0, '工作中心', '/erp/mfg/work-center', 'erp/work-center', 'tool', 106, 'MENU', 'sys:erp:mfg:workcenter:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 106);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 107, 1, 0, '产能负荷', '/erp/mfg/crp', 'erp/crp', 'bar-chart', 107, 'MENU', 'sys:erp:mfg:crp:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 107);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 545 AND 550;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 106 AND 107;
-- =====================================================================
