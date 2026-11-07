-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M6 数据分析与行动计划
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md（§3.6 / §4.2 / §5.1）
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M6）
-- 号段     : 权限 1899–1905（CRM 1800–1899 已用尽，续接 1899 起）；
--            菜单 441–448（CRM 410–449，449 预留给分析设置，暂不登记）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1899–1905）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1899, 0, 'sys:crm:analytics:dashboard', '销售看板', 'API', '/crm/analytics/dashboard', 'GET', 1899, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1899 OR perm_code = 'sys:crm:analytics:dashboard');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1900, 0, 'sys:crm:analytics:funnel', '漏斗分析', 'API', '/crm/analytics/funnel', 'GET', 1900, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1900 OR perm_code = 'sys:crm:analytics:funnel');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1901, 0, 'sys:crm:analytics:customer', '客户分析', 'API', '/crm/analytics/customer', 'GET', 1901, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1901 OR perm_code = 'sys:crm:analytics:customer');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1902, 0, 'sys:crm:analytics:ranking', '销售排行', 'API', '/crm/analytics/ranking', 'GET', 1902, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1902 OR perm_code = 'sys:crm:analytics:ranking');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1903, 0, 'sys:crm:analytics:collection', '回款分析', 'API', '/crm/analytics/collection', 'GET', 1903, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1903 OR perm_code = 'sys:crm:analytics:collection');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1904, 0, 'sys:crm:action:plan', '行动计划与行动项', 'API', '/crm/action-plan/**', 'GET,POST', 1904, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1904 OR perm_code = 'sys:crm:action:plan');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1905, 0, 'sys:crm:export', '分析数据导出', 'API', '/crm/analytics/export/**', 'GET,POST', 1905, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1905 OR perm_code = 'sys:crm:export');

-- ---------------------------------------------------------------------
-- 2. 菜单（441 销售看板 / 442 漏斗分析 / 443 客户分析 / 444 销售排行 /
--          445 回款分析 / 446 行动计划 / 447 指标快照 / 448 数据导出）
--          449 预留给分析设置，暂不登记。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 441, 1, 413, '销售看板', '/crm/dashboard', 'crm/dashboard/index', 'dashboard', 30, 'MENU',
       'sys:crm:analytics:dashboard', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 441);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 442, 1, 413, '漏斗分析', '/crm/analytics/funnel', 'crm/analytics/funnel/index', 'funnel-plot', 31, 'MENU',
       'sys:crm:analytics:funnel', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 442);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 443, 1, 413, '客户分析', '/crm/analytics/customer', 'crm/analytics/customer/index', 'team', 32, 'MENU',
       'sys:crm:analytics:customer', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 443);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 444, 1, 413, '销售排行', '/crm/analytics/ranking', 'crm/analytics/ranking/index', 'bar-chart', 33, 'MENU',
       'sys:crm:analytics:ranking', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 444);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 445, 1, 413, '回款分析', '/crm/analytics/collection', 'crm/analytics/collection/index', 'account-book', 34, 'MENU',
       'sys:crm:analytics:collection', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 445);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 446, 1, 413, '行动计划', '/crm/action-plan', 'crm/action-plan/index', 'check-square', 35, 'MENU',
       'sys:crm:action:plan', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 446);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 447, 1, 413, '指标快照', '/crm/analytics/snapshot', 'crm/analytics/snapshot/index', 'database', 36, 'MENU',
       'sys:crm:analytics:dashboard', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 447);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 448, 1, 413, '数据导出', '/crm/analytics/export', 'crm/analytics/export/index', 'export', 37, 'MENU',
       'sys:crm:export', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 448);

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / crm_admin / crm_analyst / crm_sales_manager：1899–1905
--    crm_sales：1899、1900、1902、1904（看板 / 漏斗 / 排行 / 行动计划）
--    crm_marketing：1899、1900、1904（看板 / 漏斗 / 行动计划）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1899 AND 1905
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('tenant_admin', 'crm_admin', 'crm_analyst', 'crm_sales_manager')
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
    JOIN sys_permission p ON p.id IN (1899, 1900, 1902, 1904)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code = 'crm_sales'
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
    JOIN sys_permission p ON p.id IN (1899, 1900, 1904)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code = 'crm_marketing'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
