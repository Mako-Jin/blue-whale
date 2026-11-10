-- =====================================================================
-- Wave 5.78 M2 剩余 B 第一期：ERP 采购退货 / 销售退货 权限/角色/菜单增量（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-returns.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 供应链上下文新增采购退货（红字出库 + 应付冲减）与
--            销售退货（红字入库 + 应收冲减），本脚本登记权限点与菜单。
-- 权限编号 : 已用至 506（M6 预警），本脚本使用 507–514；515–549 预留。
-- 菜单编号 : 已用至 97（M6 预警中心），本脚本使用 98–99。
-- 角色编号 : 复用平台角色 25112 erp_scm，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_scm 获得 507–514（8 项）；
--            erp_member / erp_finance 仅查询（507/511）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 采购退货权限 507–510
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 507, 0, 'sys:erp:purchase-return:list', '查询采购退货单', 'API', '/erp/scm/purchase-return/page,/erp/scm/purchase-return/{id}', 'GET', 507, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 507 OR perm_code = 'sys:erp:purchase-return:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 508, 0, 'sys:erp:purchase-return:add', '新增采购退货单', 'API', '/erp/scm/purchase-return', 'POST', 508, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 508 OR perm_code = 'sys:erp:purchase-return:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 509, 0, 'sys:erp:purchase-return:post', '采购退货过账', 'API', '/erp/scm/purchase-return/{id}/post', 'POST', 509, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 509 OR perm_code = 'sys:erp:purchase-return:post');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 510, 0, 'sys:erp:purchase-return:cancel', '取消采购退货单', 'API', '/erp/scm/purchase-return/{id}/cancel', 'POST', 510, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 510 OR perm_code = 'sys:erp:purchase-return:cancel');

-- ---------------------------------------------------------------------
-- 2. 销售退货权限 511–514
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 511, 0, 'sys:erp:sales-return:list', '查询销售退货单', 'API', '/erp/scm/sales-return/page,/erp/scm/sales-return/{id}', 'GET', 511, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 511 OR perm_code = 'sys:erp:sales-return:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 512, 0, 'sys:erp:sales-return:add', '新增销售退货单', 'API', '/erp/scm/sales-return', 'POST', 512, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 512 OR perm_code = 'sys:erp:sales-return:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 513, 0, 'sys:erp:sales-return:post', '销售退货过账', 'API', '/erp/scm/sales-return/{id}/post', 'POST', 513, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 513 OR perm_code = 'sys:erp:sales-return:post');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 514, 0, 'sys:erp:sales-return:cancel', '取消销售退货单', 'API', '/erp/scm/sales-return/{id}/cancel', 'POST', 514, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 514 OR perm_code = 'sys:erp:sales-return:cancel');

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / erp_manager / erp_scm：507–514
--    erp_member / erp_finance：507/511（查询）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 507 AND 514 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_scm')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (507, 511) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member', 'erp_finance')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 4. 菜单 98–99（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 98, 1, 0, '采购退货', '/erp/scm/purchase-return', 'erp/purchase-return', 'rollback', 98, 'MENU', 'sys:erp:purchase-return:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 98);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 99, 1, 0, '销售退货', '/erp/scm/sales-return', 'erp/sales-return', 'rollback', 99, 'MENU', 'sys:erp:sales-return:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 99);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 507 AND 514;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 507 AND 514 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：erp_scm / tenant_admin / erp_manager 各 8 项；erp_member / erp_finance 各 2 项。
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 98 AND 99;
-- =====================================================================
