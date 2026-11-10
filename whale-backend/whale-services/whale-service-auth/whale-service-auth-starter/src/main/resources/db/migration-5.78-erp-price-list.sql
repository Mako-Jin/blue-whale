-- =====================================================================
-- Wave 5.78 M2 剩余 B 第三期：ERP 价格表 权限/角色/菜单增量（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-price-list.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 供应链上下文新增价格表（客户售价 + 供应商采购价），
--            本脚本登记权限点与菜单。
-- 权限编号 : 已用至 526（第二期申请/报价），本脚本使用 527–532；533–549 预留。
-- 菜单编号 : 已用至 101（第二期申请/报价），本脚本使用 102。
-- 角色编号 : 复用平台角色 25112 erp_scm，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_scm 获得 527–532（6 项）；
--            erp_member 仅查询（527/528/532）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 价格表权限 527–532
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 527, 0, 'sys:erp:price-list:list', '查询价目表', 'API', '/erp/scm/price-list/page,/erp/scm/price-list/{id}', 'GET', 527, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 527 OR perm_code = 'sys:erp:price-list:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 528, 0, 'sys:erp:price-list:detail', '价目详情', 'API', '/erp/scm/price-list/{id}', 'GET', 528, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 528 OR perm_code = 'sys:erp:price-list:detail');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 529, 0, 'sys:erp:price-list:create', '新增价目', 'API', '/erp/scm/price-list', 'POST', 529, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 529 OR perm_code = 'sys:erp:price-list:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 530, 0, 'sys:erp:price-list:update', '更新价目', 'API', '/erp/scm/price-list/{id}', 'PUT', 530, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 530 OR perm_code = 'sys:erp:price-list:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 531, 0, 'sys:erp:price-list:toggle', '价目启停', 'API', '/erp/scm/price-list/{id}/toggle', 'POST', 531, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 531 OR perm_code = 'sys:erp:price-list:toggle');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 532, 0, 'sys:erp:price-list:resolve', '按日期取价', 'API', '/erp/scm/price-list/resolve', 'GET', 532, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 532 OR perm_code = 'sys:erp:price-list:resolve');

-- ---------------------------------------------------------------------
-- 2. 授权
--    tenant_admin / erp_manager / erp_scm：527–532（6 项）
--    erp_member：527/528/532（查询）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 527 AND 532 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_scm')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (527, 528, 532) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'erp_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 102（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 102, 1, 0, '价格表', '/erp/scm/price-list', 'erp/price-list', 'price-tag', 102, 'MENU', 'sys:erp:price-list:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 102);

-- ---------------------------------------------------------------------
-- 4. 既有业务租户按 MD5 幂等复制菜单 102（仅 tenant_type='TENANT'）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:', m.id)), 1, 15), 16, 10) AS UNSIGNED),
       t.id, 0, m.name, m.path, m.component, m.icon, m.sort, m.menu_type,
       m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.id IN (102) AND m.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = t.id AND x.path = m.path);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 527 AND 532;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 527 AND 532 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：erp_scm / tenant_admin / erp_manager 各 6 项；erp_member 3 项。
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 102;
-- =====================================================================
