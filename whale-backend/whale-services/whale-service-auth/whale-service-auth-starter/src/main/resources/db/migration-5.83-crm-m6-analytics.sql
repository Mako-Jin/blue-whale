-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M6 数据分析与行动计划
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md（§3.6 / §4.2 / §5.1）
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M6）
-- 号段     : 权限 2300–2306（**CRM 续段**）；菜单 441–448。
--
--   ⚠ 号段变更（2026-11-09）：本脚本原用 1899–1905，与 `migration-5.87-lms-m0.sql`
--     的 LMS 1900–1949 **撞号**。幂等条件 `WHERE id = ? OR perm_code = ?` 让后执行者
--     被整条跳过——LMS 先落地，于是 CRM 的 7 个分析权限一个都没进库，
--     crm_analyst / crm_marketing 打开分析页一律 403；而建表与单测全绿，从日志里看不出来。
--     权限号段改为 2300–2306（2200–2224 已归商城，2300 起空闲）。
--     授权语句同时从「按 ID 区间」改为「按 perm_code」，避免今后再被 ID 变动静默吞掉：
--     同名的 `sys:crm:export` 在存量库里是 352（Wave 5.77），按 ID 授权必然漏。
--
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（2300–2306）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2300, 0, 'sys:crm:analytics:dashboard', '销售看板', 'API', '/crm/analytics/dashboard', 'GET', 2300, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2300 OR perm_code = 'sys:crm:analytics:dashboard');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2301, 0, 'sys:crm:analytics:funnel', '漏斗分析', 'API', '/crm/analytics/funnel', 'GET', 2301, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2301 OR perm_code = 'sys:crm:analytics:funnel');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2302, 0, 'sys:crm:analytics:customer', '客户分析', 'API', '/crm/analytics/customer', 'GET', 2302, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2302 OR perm_code = 'sys:crm:analytics:customer');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2303, 0, 'sys:crm:analytics:ranking', '销售排行', 'API', '/crm/analytics/ranking', 'GET', 2303, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2303 OR perm_code = 'sys:crm:analytics:ranking');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2304, 0, 'sys:crm:analytics:collection', '回款分析', 'API', '/crm/analytics/collection', 'GET', 2304, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2304 OR perm_code = 'sys:crm:analytics:collection');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2305, 0, 'sys:crm:action:plan', '行动计划与行动项', 'API', '/crm/action-plan/**', 'GET,POST', 2305, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2305 OR perm_code = 'sys:crm:action:plan');

-- 存量库里 `sys:crm:export` 已在 352（Wave 5.77 CRM M1 导入导出），本行在那种库上是 no-op，
-- 授权按下方的 perm_code 走，不依赖此 ID 是否落地。
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2306, 0, 'sys:crm:export', '分析数据导出', 'API', '/crm/analytics/export/**', 'GET,POST', 2306, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2306 OR perm_code = 'sys:crm:export');

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
-- 3. 授权（按 perm_code，不按 ID）
--    tenant_admin / crm_admin / crm_analyst / crm_sales_manager：全部 7 项
--    crm_sales：看板 / 漏斗 / 排行 / 行动计划
--    crm_marketing：看板 / 漏斗 / 行动计划
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code IN (
             'sys:crm:analytics:dashboard', 'sys:crm:analytics:funnel',
             'sys:crm:analytics:customer', 'sys:crm:analytics:ranking',
             'sys:crm:analytics:collection', 'sys:crm:action:plan', 'sys:crm:export')
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
    JOIN sys_permission p ON p.perm_code IN (
             'sys:crm:analytics:dashboard', 'sys:crm:analytics:funnel',
             'sys:crm:analytics:ranking', 'sys:crm:action:plan')
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
    JOIN sys_permission p ON p.perm_code IN (
             'sys:crm:analytics:dashboard', 'sys:crm:analytics:funnel', 'sys:crm:action:plan')
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code = 'crm_marketing'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
