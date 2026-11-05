-- =====================================================================
-- Wave 5.78 M2 剩余 B 第四期：ERP 安全库存 权限/角色/菜单增量（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-safety-stock.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 供应链上下文新增安全库存配置、缺料预警与呆滞分析，
--            本脚本登记权限点与菜单。
-- 权限编号 : 已用至 532（第三期价格表），本脚本使用 533–538；539–549 预留。
-- 菜单编号 : 已用至 102（第三期价格表），本脚本使用 103。
-- 角色编号 : 复用平台角色 25112 erp_scm，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_scm 获得 533–538（6 项）；
--            erp_member 仅查询与分析（533/534/538）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 安全库存权限 533–538
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 533, 0, 'sys:erp:safety-stock:list', '查询安全库存', 'API', '/erp/scm/safety-stock/page', 'GET', 533, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 533 OR perm_code = 'sys:erp:safety-stock:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 534, 0, 'sys:erp:safety-stock:detail', '安全库存详情', 'API', '/erp/scm/safety-stock/{id}', 'GET', 534, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 534 OR perm_code = 'sys:erp:safety-stock:detail');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 535, 0, 'sys:erp:safety-stock:create', '新增安全库存', 'API', '/erp/scm/safety-stock', 'POST', 535, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 535 OR perm_code = 'sys:erp:safety-stock:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 536, 0, 'sys:erp:safety-stock:update', '更新安全库存', 'API', '/erp/scm/safety-stock/{id}', 'PUT', 536, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 536 OR perm_code = 'sys:erp:safety-stock:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 537, 0, 'sys:erp:safety-stock:delete', '删除安全库存', 'API', '/erp/scm/safety-stock/{id}', 'DELETE', 537, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 537 OR perm_code = 'sys:erp:safety-stock:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 538, 0, 'sys:erp:safety-stock:analysis', '缺料预警与呆滞分析', 'API', '/erp/scm/safety-stock/shortage,/erp/scm/safety-stock/idle', 'GET', 538, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 538 OR perm_code = 'sys:erp:safety-stock:analysis');

-- ---------------------------------------------------------------------
-- 2. 授权
--    tenant_admin / erp_manager / erp_scm：533–538（6 项）
--    erp_member：533/534/538（查询与分析）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 533 AND 538 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_scm')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (533, 534, 538) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'erp_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 103（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 103, 1, 0, '安全库存', '/erp/scm/safety-stock', 'erp/safety-stock', 'alert', 103, 'MENU', 'sys:erp:safety-stock:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 103);

-- ---------------------------------------------------------------------
-- 4. 既有业务租户按 MD5 幂等复制菜单 103（仅 tenant_type='TENANT'）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:', m.id)), 1, 15), 16, 10) AS UNSIGNED),
       t.id, 0, m.name, m.path, m.component, m.icon, m.sort, m.menu_type,
       m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.id IN (103) AND m.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = t.id AND x.path = m.path);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 533 AND 538;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 533 AND 538 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：erp_scm / tenant_admin / erp_manager 各 6 项；erp_member 3 项。
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 103;
-- =====================================================================
