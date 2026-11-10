-- =====================================================================
-- Wave 5.88 M-P2：党政工会党建内容权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.88-party-m2.sql
-- 幂等说明 : 权限、授权、菜单均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 权限编号 : 1959–1961（M-P0 占 1950–1953，M-P1 占 1954–1958）
--            1959 content:list / 1960 content:manage / 1961 content:audit
-- 菜单编号 : 475 党建内容（管理）/ 476 党建园地（阅读），挂在 470 工作台之下。
-- 授权口径 : 党务管理员与租户管理员拿 1959–1961 全部（含审核）；
--            支部书记拿 1959 + 1960（可投稿、可查看本人稿件与已发布内容），
--            **不给审核权**——支部自己写、自己批会让「先审后发」形同虚设。
-- 前置条件 : migration-5.88-party-m0.sql / m1.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1959–1961
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1959, 0, 'sys:party:content:list', '查看党建内容（含未审稿，按作者范围）', 'API', '/party/content,/party/content/**,/party/content/portal/**', 'GET', 1959, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1959 OR perm_code = 'sys:party:content:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1960, 0, 'sys:party:content:manage', '党建内容维护（新建/编辑/提交审核）', 'API', '/party/content,/party/content/**', 'POST', 1960, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1960 OR perm_code = 'sys:party:content:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1961, 0, 'sys:party:content:audit', '党建内容审核（通过/退回/下线）', 'API', '/party/content/**', 'POST', 1961, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1961 OR perm_code = 'sys:party:content:audit');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
-- 2.1 党务管理员与租户管理员：1959–1961 全部
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('party_admin', 'tenant_admin') AND p.id BETWEEN 1959 AND 1961 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 2.2 支部书记：查看 + 投稿（不含审核）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('branch_secretary') AND p.id IN (1959, 1960) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 475–476（挂在党政工会工作台 470 之下）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 475, 1, 470, '党建内容', '/party/content', 'party/content', 'article', 475, 'MENU', 'sys:party:content:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 475);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 476, 1, 470, '党建园地', '/party/content/portal', 'party/content-portal', 'book', 476, 'MENU', 'sys:party:browse', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 476);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1959 AND 1961 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 470 AND 476 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('party_admin','branch_secretary','tenant_admin')
--     AND rp.permission_id BETWEEN 1950 AND 1961
--   GROUP BY r.role_code;
--   -- 期望：party_admin / tenant_admin 各 12 项（1950–1961），branch_secretary 8 项
--   --       （1950/1951/1953/1954/1955/1956 + 1959/1960）。
-- =====================================================================
