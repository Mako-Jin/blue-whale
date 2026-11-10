-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M2 报价单
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 规格     : docs/specs/2026-11-04-wave5.83-crm-quote/spec.md
-- 号段     : 权限 1850–1854（1800–1899 CRM 段）；1855 预留；菜单不登记（前端未交付）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1850–1854）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1850, 0, 'sys:crm:quote:list', '查询报价', 'API', '/crm/quote/page,/crm/quote/{id}', 'GET', 1850, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1850 OR perm_code = 'sys:crm:quote:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1851, 0, 'sys:crm:quote:create', '新建报价', 'API', '/crm/quote', 'POST', 1851, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1851 OR perm_code = 'sys:crm:quote:create');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1852, 0, 'sys:crm:quote:update', '编辑报价', 'API', '/crm/quote/{id}', 'PUT', 1852, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1852 OR perm_code = 'sys:crm:quote:update');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1853, 0, 'sys:crm:quote:submit', '提交报价审批', 'API', '/crm/quote/{id}/submit', 'POST', 1853, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1853 OR perm_code = 'sys:crm:quote:submit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1854, 0, 'sys:crm:quote:revise', '报价改价新版本', 'API', '/crm/quote/{id}/revise', 'POST', 1854, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1854 OR perm_code = 'sys:crm:quote:revise');

-- ---------------------------------------------------------------------
-- 2. 授权
--    crm_admin / tenant_admin / crm_sales_manager / crm_sales：1850–1854
--    crm_marketing / crm_service / crm_analyst：仅 1850
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1850 AND 1854
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('crm_admin', 'tenant_admin', 'crm_sales_manager', 'crm_sales')
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
    JOIN sys_permission p ON p.id = 1850
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('crm_marketing', 'crm_service', 'crm_analyst')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
