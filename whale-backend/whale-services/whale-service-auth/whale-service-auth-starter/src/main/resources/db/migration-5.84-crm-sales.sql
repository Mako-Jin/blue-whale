-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M2 线索与商机
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 号段     : 权限 1840–1849（1800–1899 CRM 段）；菜单不登记（前端未交付）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1840–1849）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1840, 0, 'sys:crm:lead:list', '查询线索', 'API', '/crm/lead/page,/crm/lead/{id},/crm/lead/{id}/activity', 'GET', 1840, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1840 OR perm_code = 'sys:crm:lead:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1841, 0, 'sys:crm:lead:create', '新建线索', 'API', '/crm/lead', 'POST', 1841, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1841 OR perm_code = 'sys:crm:lead:create');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1842, 0, 'sys:crm:lead:update', '编辑线索', 'API', '/crm/lead/{id}', 'PUT', 1842, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1842 OR perm_code = 'sys:crm:lead:update');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1843, 0, 'sys:crm:lead:assign', '分配线索负责人', 'API', '/crm/lead/{id}/assign', 'POST', 1843, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1843 OR perm_code = 'sys:crm:lead:assign');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1844, 0, 'sys:crm:lead:invalid', '标记线索无效', 'API', '/crm/lead/{id}/invalid', 'POST', 1844, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1844 OR perm_code = 'sys:crm:lead:invalid');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1845, 0, 'sys:crm:lead:convert', '线索转化', 'API', '/crm/lead/{id}/convert', 'POST', 1845, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1845 OR perm_code = 'sys:crm:lead:convert');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1846, 0, 'sys:crm:opportunity:list', '查询商机与漏斗', 'API', '/crm/opportunity/page,/crm/opportunity/{id},/crm/opportunity/funnel,/crm/opportunity/{id}/history', 'GET', 1846, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1846 OR perm_code = 'sys:crm:opportunity:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1847, 0, 'sys:crm:opportunity:create', '新建商机', 'API', '/crm/opportunity', 'POST', 1847, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1847 OR perm_code = 'sys:crm:opportunity:create');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1848, 0, 'sys:crm:opportunity:update', '编辑商机', 'API', '/crm/opportunity/{id}', 'PUT', 1848, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1848 OR perm_code = 'sys:crm:opportunity:update');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1849, 0, 'sys:crm:opportunity:stage', '商机阶段流转', 'API', '/crm/opportunity/{id}/stage', 'POST', 1849, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1849 OR perm_code = 'sys:crm:opportunity:stage');

-- ---------------------------------------------------------------------
-- 2. 授权
--    crm_admin / tenant_admin / crm_sales_manager：1840–1849
--    crm_sales：1840–1842、1844–1849（不含分配）
--    crm_marketing：1840–1842、1846
--    crm_service：1840、1846
--    crm_analyst：1840、1846
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1840 AND 1849
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
    JOIN sys_permission p ON p.id IN (1840, 1841, 1842, 1844, 1845, 1846, 1847, 1848, 1849)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'crm_sales'
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
    JOIN sys_permission p ON p.id IN (1840, 1841, 1842, 1846)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'crm_marketing'
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
    JOIN sys_permission p ON p.id IN (1840, 1846)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('crm_service', 'crm_analyst')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
