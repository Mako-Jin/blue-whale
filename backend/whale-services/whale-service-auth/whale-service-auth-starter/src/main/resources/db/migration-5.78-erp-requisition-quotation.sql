-- =====================================================================
-- Wave 5.78 M2 剩余 B 第二期：ERP 采购申请 / 销售报价 权限/角色/菜单增量（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-requisition-quotation.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 供应链上下文新增采购申请（审核 → 转采购订单）与
--            销售报价（客户接受 → 转销售订单），本脚本登记权限点与菜单。
-- 权限编号 : 已用至 514（M2 退货），本脚本使用 515–526；527–549 预留。
-- 菜单编号 : 已用至 99（M2 退货），本脚本使用 100–101。
-- 角色编号 : 复用平台角色 25112 erp_scm，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_scm 获得 515–526（12 项）；
--            erp_member 仅查询（515/516/521/522）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 采购申请权限 515–520
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 515, 0, 'sys:erp:purchase-requisition:list', '查询采购申请单', 'API', '/erp/scm/purchase-requisition/page,/erp/scm/purchase-requisition/{id}', 'GET', 515, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 515 OR perm_code = 'sys:erp:purchase-requisition:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 516, 0, 'sys:erp:purchase-requisition:detail', '采购申请单详情', 'API', '/erp/scm/purchase-requisition/{id}', 'GET', 516, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 516 OR perm_code = 'sys:erp:purchase-requisition:detail');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 517, 0, 'sys:erp:purchase-requisition:create', '新增采购申请单', 'API', '/erp/scm/purchase-requisition', 'POST', 517, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 517 OR perm_code = 'sys:erp:purchase-requisition:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 518, 0, 'sys:erp:purchase-requisition:approve', '审核采购申请单', 'API', '/erp/scm/purchase-requisition/{id}/approve', 'POST', 518, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 518 OR perm_code = 'sys:erp:purchase-requisition:approve');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 519, 0, 'sys:erp:purchase-requisition:convert', '采购申请转采购订单', 'API', '/erp/scm/purchase-requisition/{id}/convert', 'POST', 519, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 519 OR perm_code = 'sys:erp:purchase-requisition:convert');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 520, 0, 'sys:erp:purchase-requisition:cancel', '取消采购申请单', 'API', '/erp/scm/purchase-requisition/{id}/cancel', 'POST', 520, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 520 OR perm_code = 'sys:erp:purchase-requisition:cancel');

-- ---------------------------------------------------------------------
-- 2. 销售报价权限 521–526
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 521, 0, 'sys:erp:sales-quotation:list', '查询销售报价单', 'API', '/erp/scm/sales-quotation/page,/erp/scm/sales-quotation/{id}', 'GET', 521, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 521 OR perm_code = 'sys:erp:sales-quotation:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 522, 0, 'sys:erp:sales-quotation:detail', '销售报价单详情', 'API', '/erp/scm/sales-quotation/{id}', 'GET', 522, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 522 OR perm_code = 'sys:erp:sales-quotation:detail');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 523, 0, 'sys:erp:sales-quotation:create', '新增销售报价单', 'API', '/erp/scm/sales-quotation', 'POST', 523, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 523 OR perm_code = 'sys:erp:sales-quotation:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 524, 0, 'sys:erp:sales-quotation:accept', '客户接受销售报价', 'API', '/erp/scm/sales-quotation/{id}/accept', 'POST', 524, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 524 OR perm_code = 'sys:erp:sales-quotation:accept');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 525, 0, 'sys:erp:sales-quotation:convert', '销售报价转销售订单', 'API', '/erp/scm/sales-quotation/{id}/convert', 'POST', 525, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 525 OR perm_code = 'sys:erp:sales-quotation:convert');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 526, 0, 'sys:erp:sales-quotation:cancel', '取消销售报价单', 'API', '/erp/scm/sales-quotation/{id}/cancel', 'POST', 526, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 526 OR perm_code = 'sys:erp:sales-quotation:cancel');

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / erp_manager / erp_scm：515–526（12 项）
--    erp_member：515/516/521/522（查询）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 515 AND 526 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_scm')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (515, 516, 521, 522) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'erp_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 4. 菜单 100–101（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 100, 1, 0, '采购申请', '/erp/scm/purchase-requisition', 'erp/purchase-requisition', 'file-add', 100, 'MENU', 'sys:erp:purchase-requisition:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 100);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 101, 1, 0, '销售报价', '/erp/scm/sales-quotation', 'erp/sales-quotation', 'tag', 101, 'MENU', 'sys:erp:sales-quotation:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 101);

-- ---------------------------------------------------------------------
-- 5. 既有业务租户按 MD5 幂等复制菜单 100–101（仅 tenant_type='TENANT'）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:', m.id)), 1, 15), 16, 10) AS UNSIGNED),
       t.id, 0, m.name, m.path, m.component, m.icon, m.sort, m.menu_type,
       m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.id IN (100, 101) AND m.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = t.id AND x.path = m.path);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 515 AND 526;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 515 AND 526 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：erp_scm / tenant_admin / erp_manager 各 12 项；erp_member 4 项。
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 100 AND 101;
-- =====================================================================
