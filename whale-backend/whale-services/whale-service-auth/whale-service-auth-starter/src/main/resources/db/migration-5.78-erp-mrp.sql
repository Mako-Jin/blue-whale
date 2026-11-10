-- =====================================================================
-- Wave 5.78 M3 剩余：ERP MRP 权限/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-mrp.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp MFG 上下文新增 MRP（BOM 展开、净需求、计划订单、需求追溯、
--            运行记录与重跑），本脚本登记权限点与菜单。
-- 权限编号 : 已用至 541（价格比价/趋势），本脚本使用 542–544，545–548 预留。
-- 菜单编号 : 已用至 103（分析中心），本脚本使用 104–105，106 预留。
-- 角色     : 沿用 25113 erp_mfg，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_mfg 获得 542–544；
--            erp_member / erp_finance 仅获得查询 543/544。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. MRP 权限 542–544
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 542, 0, 'sys:erp:mrp:run', '执行MRP运算', 'API', '/erp/mfg/mrp/runs,/erp/mfg/mrp/runs/{id}/rerun', 'POST', 542, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 542 OR perm_code = 'sys:erp:mrp:run');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 543, 0, 'sys:erp:mrp:run:list', '查询MRP运行记录', 'API', '/erp/mfg/mrp/runs/page,/erp/mfg/mrp/runs/{id}', 'GET', 543, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 543 OR perm_code = 'sys:erp:mrp:run:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 544, 0, 'sys:erp:mrp:order:list', '查询MRP计划订单', 'API', '/erp/mfg/mrp/orders/page,/erp/mfg/mrp/orders/{id}', 'GET', 544, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 544 OR perm_code = 'sys:erp:mrp:order:list');

-- ---------------------------------------------------------------------
-- 2. 授权
--    tenant_admin / erp_manager / erp_mfg：542–544
--    erp_member / erp_finance：543/544（只读）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 542 AND 544 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_mfg')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (543, 544) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member', 'erp_finance')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 104–105（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 104, 1, 0, 'MRP 运算', '/erp/mfg/mrp', 'erp/mrp', 'apartment', 104, 'MENU', 'sys:erp:mrp:run:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 104);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 105, 1, 0, '计划订单', '/erp/mfg/mrp/planned-order', 'erp/mrp-order', 'ordered-list', 105, 'MENU', 'sys:erp:mrp:order:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 105);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 542 AND 544;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 104 AND 105;
-- =====================================================================
