-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M3 发票与应收对账（7f-3）
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M3 / 7f-3）
-- 号段     : 权限 1874–1880（CRM 1800–1899）；菜单 430（CRM 410–449）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1874–1880）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1874, 0, 'sys:crm:invoice:list', '查询开票申请', 'API', '/crm/invoice/page', 'GET', 1874, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1874 OR perm_code = 'sys:crm:invoice:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1875, 0, 'sys:crm:invoice:create', '新建开票申请', 'API', '/crm/invoice', 'POST', 1875, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1875 OR perm_code = 'sys:crm:invoice:create');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1876, 0, 'sys:crm:invoice:submit', '提交开票审批', 'API', '/crm/invoice/{id}/submit', 'POST', 1876, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1876 OR perm_code = 'sys:crm:invoice:submit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1877, 0, 'sys:crm:invoice:issue', '登记开票', 'API', '/crm/invoice/{id}/issue', 'POST', 1877, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1877 OR perm_code = 'sys:crm:invoice:issue');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1878, 0, 'sys:crm:invoice:send', '登记发票寄送', 'API', '/crm/invoice/{id}/send', 'POST', 1878, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1878 OR perm_code = 'sys:crm:invoice:send');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1879, 0, 'sys:crm:invoice:red-flush', '发票红冲', 'API', '/crm/invoice/{id}/red-flush', 'POST', 1879, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1879 OR perm_code = 'sys:crm:invoice:red-flush');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1880, 0, 'sys:crm:ar:reconcile', '客户应收对账', 'API', '/crm/ar/page', 'GET', 1880, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1880 OR perm_code = 'sys:crm:ar:reconcile');

-- ---------------------------------------------------------------------
-- 2. 菜单（430 发票与应收）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 430, 1, 413, '发票与应收', '/crm/invoice', 'crm/invoice/index', 'file-text', 20, 'MENU',
       'sys:crm:invoice:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 430);

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / crm_admin / crm_sales_manager / crm_sales：1874–1880
--    crm_analyst：1874、1880（查询与应收对账）
--    crm_service：1874（仅查询）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1874 AND 1880
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
    JOIN sys_permission p ON p.id IN (1874, 1880)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code = 'crm_analyst'
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
    JOIN sys_permission p ON p.id = 1874
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code = 'crm_service'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
