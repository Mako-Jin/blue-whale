-- =====================================================================
-- Wave 5.88 M-P1：党政工会党建台账权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.88-party-m1.sql
-- 幂等说明 : 权限、授权、菜单均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 权限编号 : 1954–1958（M-P0 已占 1950–1953 基线）
--            1954 member:list / 1955 activity:list / 1956 activity:manage
--            1957 fee:list / 1958 fee:manage
-- 菜单编号 : 471 支部台账 / 472 党员档案 / 473 组织生活 / 474 党费台账（挂在 470 之下）
-- 授权口径 : 党务管理员与租户管理员拿 1954–1958 全部；
--            支部书记拿 1954 + 1955 + 1956（本支部党员查看与组织生活记录），
--            **不给党费维护**——党费收缴由支部组织委员经办、上级党组织核对，
--            书记同时掌握台账与收缴会失去内部牵制。
-- 前置条件 : migration-5.88-party-m0.sql 已执行（1950–1953 与角色 25136–25138 已生效）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1954–1958
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1954, 0, 'sys:party:member:list', '查看党员档案', 'API', '/party/member,/party/member/**', 'GET', 1954, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1954 OR perm_code = 'sys:party:member:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1955, 0, 'sys:party:activity:list', '查看组织生活', 'API', '/party/activity,/party/activity/**', 'GET', 1955, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1955 OR perm_code = 'sys:party:activity:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1956, 0, 'sys:party:activity:manage', '组织生活维护（计划/完成/归档/签到）', 'API', '/party/activity,/party/activity/**', 'POST', 1956, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1956 OR perm_code = 'sys:party:activity:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1957, 0, 'sys:party:fee:list', '查看党费标准与账单', 'API', '/party/fee/rule,/party/fee/bill/**', 'GET', 1957, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1957 OR perm_code = 'sys:party:fee:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1958, 0, 'sys:party:fee:manage', '党费维护（配标准/生成账单/登记缴费）', 'API', '/party/fee/rule,/party/fee/rule/**,/party/fee/bill/**', 'POST', 1958, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1958 OR perm_code = 'sys:party:fee:manage');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
-- 2.1 党务管理员与租户管理员：1954–1958 全部
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('party_admin', 'tenant_admin') AND p.id BETWEEN 1954 AND 1958 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 2.2 支部书记：党员查看 + 组织生活查看与记录（不含党费维护）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('branch_secretary') AND p.id IN (1954, 1955, 1956) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 471–474（挂在党政工会工作台 470 之下）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 471, 1, 470, '支部台账', '/party/branch', 'party/branch', 'cluster', 471, 'MENU', 'sys:party:branch:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 471);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 472, 1, 470, '党员档案', '/party/member', 'party/member', 'user', 472, 'MENU', 'sys:party:member:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 472);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 473, 1, 470, '组织生活', '/party/activity', 'party/activity', 'calendar', 473, 'MENU', 'sys:party:activity:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 473);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 474, 1, 470, '党费台账', '/party/fee', 'party/fee', 'money', 474, 'MENU', 'sys:party:fee:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 474);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1954 AND 1958 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 470 AND 474 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('party_admin','branch_secretary','tenant_admin')
--     AND rp.permission_id BETWEEN 1950 AND 1958
--   GROUP BY r.role_code;
--   -- 期望：party_admin / tenant_admin 各 9 项（1950–1958），branch_secretary 6 项
--   --       （1950/1951/1953 + 1954/1955/1956）。
-- =====================================================================
