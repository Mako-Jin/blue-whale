-- =====================================================================
-- Wave 5.78 M2 剩余 A：ERP 库存调拨 / 盘点 权限/角色/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-stock-ops.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 供应链上下文新增库存调拨（同成本跨仓结转）与库存盘点
--            （差异生成流水 + 盘盈盘亏凭证）；本脚本登记权限点与菜单。
-- 权限编号 : 已用至 432，本脚本使用 433–440；441–469 预留。
-- 菜单编号 : 已用至 81，本脚本使用 82–83；84–119 预留。
-- 角色编号 : 复用平台角色 25112 erp_scm，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_scm 获得 433–440（8 项）；
--            erp_member 仅获得查询（433/437）；
--            erp_finance 获得 433/437（核对库存成本与盘点差异）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 库存调拨权限 433–436
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 433, 0, 'sys:erp:transfer:list', '查询库存调拨单', 'API', '/erp/scm/transfer/page,/erp/scm/transfer/{id}', 'GET', 433, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 433 OR perm_code = 'sys:erp:transfer:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 434, 0, 'sys:erp:transfer:add', '新增库存调拨单', 'API', '/erp/scm/transfer', 'POST', 434, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 434 OR perm_code = 'sys:erp:transfer:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 435, 0, 'sys:erp:transfer:post', '库存调拨过账', 'API', '/erp/scm/transfer/{id}/post', 'POST', 435, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 435 OR perm_code = 'sys:erp:transfer:post');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 436, 0, 'sys:erp:transfer:cancel', '取消库存调拨单', 'API', '/erp/scm/transfer/{id}/cancel', 'POST', 436, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 436 OR perm_code = 'sys:erp:transfer:cancel');

-- ---------------------------------------------------------------------
-- 2. 库存盘点权限 437–440
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 437, 0, 'sys:erp:stocktake:list', '查询库存盘点单', 'API', '/erp/scm/stocktake/page,/erp/scm/stocktake/{id}', 'GET', 437, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 437 OR perm_code = 'sys:erp:stocktake:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 438, 0, 'sys:erp:stocktake:add', '新增库存盘点单', 'API', '/erp/scm/stocktake', 'POST', 438, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 438 OR perm_code = 'sys:erp:stocktake:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 439, 0, 'sys:erp:stocktake:post', '库存盘点过账', 'API', '/erp/scm/stocktake/{id}/post', 'POST', 439, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 439 OR perm_code = 'sys:erp:stocktake:post');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 440, 0, 'sys:erp:stocktake:cancel', '取消库存盘点单', 'API', '/erp/scm/stocktake/{id}/cancel', 'POST', 440, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 440 OR perm_code = 'sys:erp:stocktake:cancel');

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / erp_manager / erp_scm：433–440
--    erp_member：433/437
--    erp_finance：433/437
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 433 AND 440 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_scm')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (433, 437) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member', 'erp_finance')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 4. 菜单 82–83（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 82, 1, 0, '库存调拨', '/erp/scm/transfer', 'erp/transfer', 'swap', 82, 'MENU', 'sys:erp:transfer:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 82);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 83, 1, 0, '库存盘点', '/erp/scm/stocktake', 'erp/stocktake', 'audit', 83, 'MENU', 'sys:erp:stocktake:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 83);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 433 AND 440;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 433 AND 440 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：erp_scm / tenant_admin / erp_manager 各 8 项；erp_member 2 项；erp_finance 2 项。
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 82 AND 83;
-- =====================================================================
