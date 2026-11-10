-- =====================================================================
-- Wave 5.78 M0+M1：ERP 主数据与财务核心权限/角色/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-finance.sql
-- 幂等说明 : 权限、角色、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : ERP 采用模块化单体（whale-erp，9001，/erp/**），本脚本登记
--            M0 主数据（科目/期间/客户/仓库/参考数据）与 M1 财务核心
--            （凭证/应收应付/账龄/报表）的权限点、角色模板与菜单。
-- 权限编号 : 已用至 354（CRM），本脚本使用 355–367（主数据 13 项）
--            与 375–387（财务 13 项）；368–374 预留。
-- 菜单编号 : 已用至 56（场景）；本脚本使用 63–66；57–62 为 CRM 预留。
-- 角色编号 : 平台角色 25110 erp_finance / 25111 erp_masterdata；
--            业务租户复制角色与授权 ID 采用「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : tenant_admin / erp_manager / erp_finance 获得 355–367 与 375–387；
--            erp_masterdata 获得 355–367；
--            erp_member 仅获得查询与报表（355/358/361/364/367/375/379/383/386/387）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点：主数据 355–367
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 355, 0, 'sys:erp:account:list', '查询会计科目', 'API', '/erp/master-data/account/page', 'GET', 355, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 355 OR perm_code = 'sys:erp:account:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 356, 0, 'sys:erp:account:add', '新增会计科目', 'API', '/erp/master-data/account', 'POST', 356, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 356 OR perm_code = 'sys:erp:account:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 357, 0, 'sys:erp:account:edit', '编辑会计科目', 'API', '/erp/master-data/account/{id}', 'PUT', 357, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 357 OR perm_code = 'sys:erp:account:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 358, 0, 'sys:erp:period:list', '查询会计期间', 'API', '/erp/master-data/period/page', 'GET', 358, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 358 OR perm_code = 'sys:erp:period:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 359, 0, 'sys:erp:period:close', '会计期间关账', 'API', '/erp/master-data/period/{id}/close', 'POST', 359, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 359 OR perm_code = 'sys:erp:period:close');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 360, 0, 'sys:erp:period:reopen', '会计期间反结账', 'API', '/erp/master-data/period/{id}/reopen', 'POST', 360, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 360 OR perm_code = 'sys:erp:period:reopen');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 361, 0, 'sys:erp:customer:list', '查询客户主数据', 'API', '/erp/master-data/customer/page', 'GET', 361, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 361 OR perm_code = 'sys:erp:customer:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 362, 0, 'sys:erp:customer:add', '新增客户主数据', 'API', '/erp/master-data/customer', 'POST', 362, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 362 OR perm_code = 'sys:erp:customer:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 363, 0, 'sys:erp:customer:edit', '编辑客户主数据', 'API', '/erp/master-data/customer/{id}', 'PUT', 363, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 363 OR perm_code = 'sys:erp:customer:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 364, 0, 'sys:erp:warehouse:list', '查询仓库', 'API', '/erp/master-data/warehouse/page', 'GET', 364, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 364 OR perm_code = 'sys:erp:warehouse:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 365, 0, 'sys:erp:warehouse:add', '新增仓库', 'API', '/erp/master-data/warehouse', 'POST', 365, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 365 OR perm_code = 'sys:erp:warehouse:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 366, 0, 'sys:erp:warehouse:edit', '编辑仓库', 'API', '/erp/master-data/warehouse/{id}', 'PUT', 366, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 366 OR perm_code = 'sys:erp:warehouse:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 367, 0, 'sys:erp:reference:list', '查询参考数据', 'API', '/erp/master-data/reference', 'GET', 367, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 367 OR perm_code = 'sys:erp:reference:list');

-- ---------------------------------------------------------------------
-- 2. 平台权限点：财务 375–387（368–374 预留）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 375, 0, 'sys:erp:voucher:list', '查询会计凭证', 'API', '/erp/finance/voucher/page,/erp/finance/voucher/{id}', 'GET', 375, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 375 OR perm_code = 'sys:erp:voucher:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 376, 0, 'sys:erp:voucher:add', '录入会计凭证', 'API', '/erp/finance/voucher', 'POST', 376, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 376 OR perm_code = 'sys:erp:voucher:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 377, 0, 'sys:erp:voucher:post', '凭证记账', 'API', '/erp/finance/voucher/{id}/post', 'POST', 377, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 377 OR perm_code = 'sys:erp:voucher:post');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 378, 0, 'sys:erp:voucher:reverse', '凭证红冲', 'API', '/erp/finance/voucher/{id}/reverse', 'POST', 378, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 378 OR perm_code = 'sys:erp:voucher:reverse');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 379, 0, 'sys:erp:receivable:list', '查询应收单', 'API', '/erp/finance/receivable/page', 'GET', 379, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 379 OR perm_code = 'sys:erp:receivable:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 380, 0, 'sys:erp:receivable:add', '新增应收单', 'API', '/erp/finance/receivable', 'POST', 380, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 380 OR perm_code = 'sys:erp:receivable:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 381, 0, 'sys:erp:receivable:settle', '应收核销', 'API', '/erp/finance/receivable/{id}/settle', 'POST', 381, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 381 OR perm_code = 'sys:erp:receivable:settle');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 382, 0, 'sys:erp:receivable:bad-debt', '应收坏账计提', 'API', '/erp/finance/receivable/{id}/bad-debt', 'POST', 382, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 382 OR perm_code = 'sys:erp:receivable:bad-debt');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 383, 0, 'sys:erp:payable:list', '查询应付单', 'API', '/erp/finance/payable/page', 'GET', 383, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 383 OR perm_code = 'sys:erp:payable:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 384, 0, 'sys:erp:payable:add', '新增应付单', 'API', '/erp/finance/payable', 'POST', 384, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 384 OR perm_code = 'sys:erp:payable:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 385, 0, 'sys:erp:payable:settle', '应付付款核销', 'API', '/erp/finance/payable/{id}/settle', 'POST', 385, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 385 OR perm_code = 'sys:erp:payable:settle');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 386, 0, 'sys:erp:aging:view', '查看应收应付账龄', 'API', '/erp/finance/aging/receivable,/erp/finance/aging/payable', 'GET', 386, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 386 OR perm_code = 'sys:erp:aging:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 387, 0, 'sys:erp:report:view', '查看财务报表', 'API', '/erp/finance/report/ledger,/erp/finance/report/account-balance,/erp/finance/report/balance-sheet,/erp/finance/report/income-statement', 'GET', 387, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 387 OR perm_code = 'sys:erp:report:view');

-- ---------------------------------------------------------------------
-- 3. 平台租户业务角色模板（25110 erp_finance / 25111 erp_masterdata）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25110, 1, 'erp_finance', 'ERP 财务', '主数据与财务核心全量：科目/期间/客户/仓库、凭证、应收应付、账龄与报表', 260, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25110 OR (tenant_id = 1 AND role_code = 'erp_finance'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25111, 1, 'erp_masterdata', 'ERP 主数据维护', '科目/期间/客户/仓库与参考数据维护，不含凭证与应收应付', 261, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25111 OR (tenant_id = 1 AND role_code = 'erp_masterdata'));

-- ---------------------------------------------------------------------
-- 4. 既有业务租户复制 ERP 财务 / 主数据角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'erp_finance' AS role_code, 'ERP 财务' AS role_name, '主数据与财务核心全量：科目/期间/客户/仓库、凭证、应收应付、账龄与报表' AS description, 260 AS sort_no UNION ALL SELECT 'erp_masterdata', 'ERP 主数据维护', '科目/期间/客户/仓库与参考数据维护，不含凭证与应收应付', 261 ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 5. 授权（按角色编码匹配，覆盖平台与全部业务租户）
--    tenant_admin / erp_manager / erp_finance：355–367 + 375–387
--    erp_masterdata：355–367
--    erp_member：355/358/361/364/367/375/379/383/386/387
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON (p.id BETWEEN 355 AND 367 OR p.id BETWEEN 375 AND 387) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_finance')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 355 AND 367 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'erp_masterdata') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (355, 358, 361, 364, 367, 375, 379, 383, 386, 387) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'erp_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 6. 菜单 63–66（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 63, 1, 0, 'ERP 主数据', '/erp/master-data', 'erp/masterdata', 'database', 63, 'MENU', 'sys:erp:account:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 63);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 64, 1, 0, '会计凭证', '/erp/finance/voucher', 'erp/voucher', 'audit', 64, 'MENU', 'sys:erp:voucher:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 64);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 65, 1, 0, '应收应付', '/erp/finance/arap', 'erp/arap', 'account-book', 65, 'MENU', 'sys:erp:receivable:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 65);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 66, 1, 0, '财务报表', '/erp/finance/report', 'erp/report', 'bar-chart', 66, 'MENU', 'sys:erp:report:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 66);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 355 AND 387;
--   SELECT tenant_id, role_code FROM sys_role WHERE role_code IN ('erp_finance','erp_masterdata') ORDER BY tenant_id;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 355 AND 387 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：erp_finance / tenant_admin / erp_manager 各 26 项；
--   --       erp_masterdata 各 13 项；erp_member 各 10 项。
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 63 AND 66;
-- =====================================================================
