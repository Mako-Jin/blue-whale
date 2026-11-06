-- =====================================================================
-- Wave 5.87 M4-1：学习培训考试（whale-lms）证书模板与证书权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-lms-m4a.sql
-- 幂等说明 : 权限、菜单、授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-lms M4-1 证书（模板定义发证口径 + 证书发放/撤销/换发）。
-- 权限编号 : LMS 段 1900–1949；M3-1 用 1922–1930，M3-2 用 1931–1940，
--            本脚本使用 1944–1949，其中 1949（学分）为 M4-2 预留登记。
-- 菜单编号 : LMS 段 450–469；本脚本使用 457（证书），458（学分）留待 M4-2。
-- 授权口径 : lms_admin、tenant_admin 获得 1944–1949 全量；
--            lms_lecturer 获得 1944/1948（看证书 + 我的证书，不含发证与撤销）；
--            lms_member 获得 1948（仅我的证书）。
-- 设计说明 : 模板维护（1945）、发证（1946）、撤销换发（1947）三权分设——
--            能发证的人不必然能改发证口径，能撤销的人也不必然是发证的人。
-- 前置条件 : bluewhale_lms 已由 Flyway V6 建表；migration-5.87-lms-m3b.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1944–1949
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1944, 0, 'sys:lms:cert:list', '查询证书与模板', 'API', '/lms/cert/template/page,/lms/cert/template/{id},/lms/certificate/page,/lms/certificate/{id}', 'GET', 1944, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1944 OR perm_code = 'sys:lms:cert:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1945, 0, 'sys:lms:cert:template', '维护证书模板', 'API', '/lms/cert/template,/lms/cert/template/{id},/lms/cert/template/{id}/status', 'POST,PUT,DELETE', 1945, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1945 OR perm_code = 'sys:lms:cert:template');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1946, 0, 'sys:lms:cert:issue', '发放证书', 'API', '/lms/certificate/issue', 'POST', 1946, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1946 OR perm_code = 'sys:lms:cert:issue');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1947, 0, 'sys:lms:cert:revoke', '撤销与换发证书', 'API', '/lms/certificate/{id}/revoke,/lms/certificate/{id}/renew', 'PUT,POST', 1947, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1947 OR perm_code = 'sys:lms:cert:revoke');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1948, 0, 'sys:lms:cert:mine', '我的证书', 'API', '/lms/certificate/mine', 'GET', 1948, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1948 OR perm_code = 'sys:lms:cert:mine');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1949, 0, 'sys:lms:credit:manage', '学分规则与台账', 'API', '/lms/credit/**', 'GET,POST,PUT,DELETE', 1949, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1949 OR perm_code = 'sys:lms:credit:manage');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('lms_admin', 'tenant_admin') AND p.id BETWEEN 1944 AND 1949) OR (r.role_code = 'lms_lecturer' AND p.id IN (1944, 1948)) OR (r.role_code = 'lms_member' AND p.id = 1948) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 457（证书），挂在 450 学习培训下
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 457, 1, 450, '证书', '/lms/cert', 'lms/cert/index', 'trophy', 457, 'MENU', 'sys:lms:cert:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 457);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1944 AND 1949 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('lms_admin','lms_lecturer','lms_member','tenant_admin')
--   GROUP BY r.role_code;
--   -- 期望：lms_admin/tenant_admin ≥6、lms_lecturer ≥2、lms_member ≥1（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id = 457;
-- =====================================================================
