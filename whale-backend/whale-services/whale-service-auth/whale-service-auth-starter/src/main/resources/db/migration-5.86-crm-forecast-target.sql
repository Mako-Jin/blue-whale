-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M2 销售预测与销售目标
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 规格     : docs/specs/2026-11-04-wave5.83-crm-forecast-target/spec.md
-- 号段     : 权限 1855–1859（1800–1899 CRM 段）；菜单不登记（前端未交付）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1855–1859）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1855, 0, 'sys:crm:forecast:list', '查询销售预测', 'API',
       '/crm/forecast/page,/crm/forecast/{id},/crm/forecast/{id}/adjustments', 'GET', 1855, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1855 OR perm_code = 'sys:crm:forecast:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1856, 0, 'sys:crm:forecast:manage', '生成与调整销售预测', 'API',
       '/crm/forecast/generate,/crm/forecast/{id}/adjust', 'POST,PUT', 1856, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1856 OR perm_code = 'sys:crm:forecast:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1857, 0, 'sys:crm:target:list', '查询销售目标', 'API',
       '/crm/sales-target/page,/crm/sales-target/{id},/crm/sales-target/{id}/completion,/crm/sales-target/{id}/adjustments',
       'GET', 1857, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1857 OR perm_code = 'sys:crm:target:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1858, 0, 'sys:crm:target:manage', '新建与调整销售目标', 'API',
       '/crm/sales-target,/crm/sales-target/{id}', 'POST,PUT', 1858, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1858 OR perm_code = 'sys:crm:target:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1859, 0, 'sys:crm:target:delete', '删除销售目标', 'API', '/crm/sales-target/{id}', 'DELETE', 1859, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1859 OR perm_code = 'sys:crm:target:delete');

-- ---------------------------------------------------------------------
-- 2. 授权
--    crm_admin / tenant_admin / crm_sales_manager：1855–1859
--    crm_sales / crm_analyst：仅 1855、1857（查询）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1855 AND 1859
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('crm_admin', 'tenant_admin', 'crm_sales_manager')
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
    JOIN sys_permission p ON p.id IN (1855, 1857)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('crm_sales', 'crm_analyst')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
