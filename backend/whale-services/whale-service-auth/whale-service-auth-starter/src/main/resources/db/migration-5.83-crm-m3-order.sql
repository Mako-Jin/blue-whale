-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M3 商务订单（7f-2）
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M3 / 7f-2）
-- 号段     : 权限 1866–1873（CRM 1800–1899）；菜单 429（CRM 410–449）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1866–1873）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1866, 0, 'sys:crm:order:list', '查询商务订单', 'API', '/crm/order/page', 'GET', 1866, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1866 OR perm_code = 'sys:crm:order:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1867, 0, 'sys:crm:order:detail', '查看商务订单详情', 'API', '/crm/order/{id}', 'GET', 1867, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1867 OR perm_code = 'sys:crm:order:detail');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1868, 0, 'sys:crm:order:create', '新建商务订单', 'API', '/crm/order', 'POST', 1868, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1868 OR perm_code = 'sys:crm:order:create');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1869, 0, 'sys:crm:order:update', '编辑商务订单', 'API', '/crm/order/{id}', 'PUT', 1869, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1869 OR perm_code = 'sys:crm:order:update');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1870, 0, 'sys:crm:order:submit', '提交商务订单审批', 'API', '/crm/order/{id}/submit', 'POST', 1870, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1870 OR perm_code = 'sys:crm:order:submit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1871, 0, 'sys:crm:order:retry', '重试下发商务订单', 'API',
       '/crm/order/{id}/retry', 'POST', 1871, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1871 OR perm_code = 'sys:crm:order:retry');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1872, 0, 'sys:crm:order:cancel', '取消商务订单', 'API', '/crm/order/{id}/cancel', 'POST', 1872, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1872 OR perm_code = 'sys:crm:order:cancel');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1873, 0, 'sys:crm:order:reconcile', '对账商务订单履约状态', 'API',
       '/crm/order/reconcile', 'POST', 1873, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1873 OR perm_code = 'sys:crm:order:reconcile');

-- ---------------------------------------------------------------------
-- 2. 菜单（429 商务订单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 429, 1, 413, '商务订单', '/crm/order', 'crm/order/index', 'shopping-cart', 19, 'MENU',
       'sys:crm:order:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 429);

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / crm_admin / crm_sales_manager / crm_sales：1866–1873
--    crm_analyst / crm_service：仅 1866、1867（查询与详情）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1866 AND 1873
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('tenant_admin', 'crm_admin', 'crm_sales_manager', 'crm_sales')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id IN (1866, 1867)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('crm_analyst', 'crm_service')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
