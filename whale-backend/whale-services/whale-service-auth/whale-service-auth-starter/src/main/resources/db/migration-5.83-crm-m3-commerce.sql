-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M3 合同台账 + 回款计划（7f-1）
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M3 / 7f-1）
-- 号段     : 权限 1860–1865（CRM 1800–1899）；菜单 427–428（CRM 410–449）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1860–1865）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1860, 0, 'sys:crm:contract:list', '查询合同台账', 'API',
       '/crm/contract/page,/crm/contract/{id},/crm/contract/expiring', 'GET', 1860, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1860 OR perm_code = 'sys:crm:contract:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1861, 0, 'sys:crm:contract:sync', '同步合同投影', 'API', '/crm/contract/sync', 'POST', 1861, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1861 OR perm_code = 'sys:crm:contract:sync');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1862, 0, 'sys:crm:contract:renewal', '标记合同续约', 'API',
       '/crm/contract/{id}/renewal', 'PUT', 1862, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1862 OR perm_code = 'sys:crm:contract:renewal');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1863, 0, 'sys:crm:collection:list', '查询回款计划与催收记录', 'API',
       '/crm/collection/page,/crm/collection/{id},/crm/collection/{id}/actions', 'GET', 1863, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1863 OR perm_code = 'sys:crm:collection:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1864, 0, 'sys:crm:collection:manage', '维护回款计划分期', 'API',
       '/crm/collection/plan', 'PUT', 1864, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1864 OR perm_code = 'sys:crm:collection:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1865, 0, 'sys:crm:collection:action', '登记承诺还款与催收记录', 'API',
       '/crm/collection/{id}/promise,/crm/collection/{id}/action', 'POST', 1865, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1865 OR perm_code = 'sys:crm:collection:action');

-- ---------------------------------------------------------------------
-- 2. 菜单（427 合同台账 / 428 回款计划）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 427, 1, 413, '合同台账', '/crm/contract', 'crm/contract/index', 'file-text', 17, 'MENU',
       'sys:crm:contract:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 427);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 428, 1, 413, '回款计划', '/crm/collection', 'crm/collection/index', 'money', 18, 'MENU',
       'sys:crm:collection:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 428);

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / crm_admin / crm_sales_manager / crm_sales：1860–1865
--    crm_analyst / crm_service / crm_marketing：仅 1860、1863（查询）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1860 AND 1865
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
    JOIN sys_permission p ON p.id IN (1860, 1863)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('crm_analyst', 'crm_service', 'crm_marketing')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
