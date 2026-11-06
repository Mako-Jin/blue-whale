-- =====================================================================
-- Wave 5.88 M-P4：党政工会「看板与收口」权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.88-party-m4.sql
-- 幂等说明 : 权限、授权、菜单均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 权限编号 : 1962–1965（党建空闲段，M-P4 看板收口）与 1981–1982（工会空闲段）
--            1962 party:metric:list / 1963 metric:manage / 1964 metric:export
--            1965 notice:list
--            1981 union:metric:list / 1982 union:metric:manage
-- 菜单编号 : 477 数据看板 / 478 通知与提醒 / 486 工会看板（挂在 470 党政工会工作台之下）
-- 授权口径 : 党务管理员与租户管理员拿 1962–1965 全部；
--            支部书记拿 1962 与 1965（只能看本支部看板与本支部通知留痕）；
--            工会管理员与租户管理员拿 1981–1982；
--            党务管理员拿 1981（可查看工会看板，不触发刷新与通知扫描）。
-- 前置条件 : migration-5.88-party-m0.sql / m1.sql / m2.sql / m3.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1962–1965、1981–1982
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1962, 0, 'sys:party:metric:list', '党务看板与换届到期查看', 'API', '/party/metric,/party/metric/**,/party/branch/term-due', 'GET', 1962, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1962 OR perm_code = 'sys:party:metric:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1963, 0, 'sys:party:metric:manage', '统计维护（刷新快照/提醒扫描/员工同步）', 'API', '/party/metric/**,/party/notice/scan,/party/integration/employee/sync', 'POST', 1963, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1963 OR perm_code = 'sys:party:metric:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1964, 0, 'sys:party:metric:export', '党务聚合数据脱敏导出', 'API', '/party/metric/export', 'GET', 1964, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1964 OR perm_code = 'sys:party:metric:export');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1965, 0, 'sys:party:notice:list', '查看通知与提醒留痕', 'API', '/party/notice,/party/notice/**', 'GET', 1965, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1965 OR perm_code = 'sys:party:notice:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1981, 0, 'sys:union:metric:list', '工会看板查看与导出', 'API', '/party/union/metric,/party/union/metric/**', 'GET', 1981, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1981 OR perm_code = 'sys:union:metric:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1982, 0, 'sys:union:metric:manage', '工会统计维护（刷新快照/活动通知扫描）', 'API', '/party/union/metric/**,/party/union/notice/scan', 'POST', 1982, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1982 OR perm_code = 'sys:union:metric:manage');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
-- 2.1 党务管理员与租户管理员：1962–1965 全部
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('party_admin', 'tenant_admin') AND p.id BETWEEN 1962 AND 1965 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 2.2 支部书记：仅本支部看板与通知留痕（1962 / 1965）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('branch_secretary') AND p.id IN (1962, 1965) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 2.3 工会管理员与租户管理员：1981–1982
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('union_admin', 'tenant_admin') AND p.id BETWEEN 1981 AND 1982 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 2.4 党务管理员：仅查看工会看板（1981）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('party_admin') AND p.id = 1981 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 477 / 478 / 486（挂在党政工会工作台 470 之下）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 477, 1, 470, '数据看板', '/party/dashboard', 'party/dashboard', 'chart', 477, 'MENU', 'sys:party:metric:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 477);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 478, 1, 470, '通知与提醒', '/party/notice', 'party/notice', 'bell', 478, 'MENU', 'sys:party:notice:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 478);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 486, 1, 470, '工会看板', '/party/union/dashboard', 'party/union-dashboard', 'chart', 486, 'MENU', 'sys:union:metric:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 486);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1962 AND 1965 OR id BETWEEN 1981 AND 1982 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id IN (477, 478, 486) ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('party_admin','branch_secretary','union_admin','tenant_admin')
--     AND ((rp.permission_id BETWEEN 1962 AND 1965) OR rp.permission_id BETWEEN 1981 AND 1982)
--   GROUP BY r.role_code;
--   -- 期望：party_admin 5 项（1962–1965 + 1981）；branch_secretary 2 项（1962 / 1965）；
--   --       union_admin 2 项（1981–1982）；tenant_admin 6 项（1962–1965 + 1981–1982）。
-- =====================================================================
