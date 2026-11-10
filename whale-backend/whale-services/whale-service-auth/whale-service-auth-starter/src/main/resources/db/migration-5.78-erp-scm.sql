-- =====================================================================
-- Wave 5.78 M2：ERP 供应链核心权限/角色/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-scm.sql
-- 幂等说明 : 权限、角色、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 模块化单体内的 SCM 上下文（采购订单、销售订单、库存），
--            收货/发货自动生成应付/应收与凭证；本脚本登记权限点、角色与菜单。
-- 权限编号 : 已用至 387（财务），本脚本使用 420–432；425 预留。
-- 菜单编号 : 已用至 66（ERP 财务），本脚本使用 79–81；67–78 预留。
-- 角色编号 : 平台角色 25112 erp_scm；业务租户复制角色与授权 ID 采用
--            「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : tenant_admin / erp_manager / erp_scm 获得 420–432（12 项）；
--            erp_member 仅获得查询（420/426/431）；
--            erp_finance 额外获得库存查询 431（对账需要）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 采购权限 420–424
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 420, 0, 'sys:erp:purchase:list', '查询采购订单', 'API', '/erp/scm/purchase-order/page,/erp/scm/purchase-order/{id}', 'GET', 420, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 420 OR perm_code = 'sys:erp:purchase:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 421, 0, 'sys:erp:purchase:add', '新增采购订单', 'API', '/erp/scm/purchase-order', 'POST', 421, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 421 OR perm_code = 'sys:erp:purchase:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 422, 0, 'sys:erp:purchase:approve', '审核采购订单', 'API', '/erp/scm/purchase-order/{id}/approve', 'POST', 422, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 422 OR perm_code = 'sys:erp:purchase:approve');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 423, 0, 'sys:erp:purchase:receive', '采购收货', 'API', '/erp/scm/purchase-order/{id}/receive', 'POST', 423, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 423 OR perm_code = 'sys:erp:purchase:receive');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 424, 0, 'sys:erp:purchase:cancel', '取消采购订单', 'API', '/erp/scm/purchase-order/{id}/cancel', 'POST', 424, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 424 OR perm_code = 'sys:erp:purchase:cancel');

-- ---------------------------------------------------------------------
-- 2. 销售权限 426–430（425 预留）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 426, 0, 'sys:erp:sales:list', '查询销售订单', 'API', '/erp/scm/sales-order/page,/erp/scm/sales-order/{id}', 'GET', 426, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 426 OR perm_code = 'sys:erp:sales:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 427, 0, 'sys:erp:sales:add', '新增销售订单', 'API', '/erp/scm/sales-order', 'POST', 427, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 427 OR perm_code = 'sys:erp:sales:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 428, 0, 'sys:erp:sales:approve', '审核销售订单', 'API', '/erp/scm/sales-order/{id}/approve', 'POST', 428, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 428 OR perm_code = 'sys:erp:sales:approve');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 429, 0, 'sys:erp:sales:ship', '销售发货', 'API', '/erp/scm/sales-order/{id}/ship', 'POST', 429, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 429 OR perm_code = 'sys:erp:sales:ship');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 430, 0, 'sys:erp:sales:cancel', '取消销售订单', 'API', '/erp/scm/sales-order/{id}/cancel', 'POST', 430, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 430 OR perm_code = 'sys:erp:sales:cancel');

-- ---------------------------------------------------------------------
-- 3. 库存权限 431–432
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 431, 0, 'sys:erp:stock:list', '查询库存余额与流水', 'API', '/erp/scm/stock/balance/page,/erp/scm/stock/movement/page', 'GET', 431, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 431 OR perm_code = 'sys:erp:stock:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 432, 0, 'sys:erp:stock:adjust', '库存调整（盘盈盘亏）', 'API', '/erp/scm/stock/adjust', 'POST', 432, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 432 OR perm_code = 'sys:erp:stock:adjust');

-- ---------------------------------------------------------------------
-- 4. 平台租户业务角色模板 25112 erp_scm
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25112, 1, 'erp_scm', 'ERP 供应链', '采购订单、销售订单、库存台账与收发存操作', 262, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25112 OR (tenant_id = 1 AND role_code = 'erp_scm'));

-- ---------------------------------------------------------------------
-- 5. 既有业务租户复制 ERP 供应链角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'erp_scm' AS role_code, 'ERP 供应链' AS role_name, '采购订单、销售订单、库存台账与收发存操作' AS description, 262 AS sort_no ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 6. 授权
--    tenant_admin / erp_manager / erp_scm：420–432
--    erp_member：420/426/431
--    erp_finance：431
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 420 AND 432 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_scm')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (420, 426, 431) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'erp_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id = 431 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'erp_finance') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 7. 菜单 79–81（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 79, 1, 0, '采购订单', '/erp/scm/purchase', 'erp/purchase', 'shopping-cart', 79, 'MENU', 'sys:erp:purchase:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 79);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 80, 1, 0, '销售订单', '/erp/scm/sales', 'erp/sales', 'shop', 80, 'MENU', 'sys:erp:sales:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 80);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 81, 1, 0, '库存台账', '/erp/scm/stock', 'erp/stock', 'inbox', 81, 'MENU', 'sys:erp:stock:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 81);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 420 AND 432;
--   SELECT tenant_id, role_code FROM sys_role WHERE role_code = 'erp_scm' ORDER BY tenant_id;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 420 AND 432 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：erp_scm / tenant_admin / erp_manager 各 12 项；erp_member 3 项；erp_finance 1 项。
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 79 AND 81;
-- =====================================================================
