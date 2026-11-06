-- =====================================================================
-- Wave 5.87 M3-1：学习培训考试（whale-lms）题库与试卷权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-lms-m3a.sql
-- 幂等说明 : 权限、菜单、授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-lms M3-1 题库（题目/选项）与试卷（试卷/试卷题目/规则抽题）。
-- 权限编号 : LMS 段 1900–1949；M2 已用 1911–1921，本脚本使用 1922–1930；
--            M3-2 考试与判分从 1931 起编，M4 证书学分从 1944 起编。
-- 菜单编号 : LMS 段 450–469；本脚本使用 453（题库）、454（试卷）。
-- 授权口径 : lms_admin、tenant_admin 获得 1922–1930 全量；
--            lms_lecturer 获得 1922/1923/1924/1926/1927/1928（出题与组卷，不含删除与发布）；
--            lms_member 不授予（学员只能答题，不能进题库与试卷）。
-- 前置条件 : bluewhale_lms 已由 Flyway V4 建表；migration-5.87-lms-m2.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1922–1930
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1922, 0, 'sys:lms:question:list', '查询题库', 'API', '/lms/question/page,/lms/question/{id}', 'GET', 1922, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1922 OR perm_code = 'sys:lms:question:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1923, 0, 'sys:lms:question:create', '新建题目', 'API', '/lms/question', 'POST', 1923, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1923 OR perm_code = 'sys:lms:question:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1924, 0, 'sys:lms:question:update', '编辑题目与启停', 'API', '/lms/question/{id},/lms/question/{id}/status', 'PUT', 1924, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1924 OR perm_code = 'sys:lms:question:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1925, 0, 'sys:lms:question:delete', '删除题目', 'API', '/lms/question/{id}', 'DELETE', 1925, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1925 OR perm_code = 'sys:lms:question:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1926, 0, 'sys:lms:paper:list', '查询试卷', 'API', '/lms/paper/page,/lms/paper/{id}', 'GET', 1926, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1926 OR perm_code = 'sys:lms:paper:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1927, 0, 'sys:lms:paper:create', '新建试卷', 'API', '/lms/paper', 'POST', 1927, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1927 OR perm_code = 'sys:lms:paper:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1928, 0, 'sys:lms:paper:update', '编辑试卷与抽题', 'API', '/lms/paper/{id},/lms/paper/{id}/question,/lms/paper/{id}/generate', 'PUT,POST,DELETE', 1928, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1928 OR perm_code = 'sys:lms:paper:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1929, 0, 'sys:lms:paper:delete', '删除试卷', 'API', '/lms/paper/{id}', 'DELETE', 1929, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1929 OR perm_code = 'sys:lms:paper:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1930, 0, 'sys:lms:paper:publish', '发布与归档试卷', 'API', '/lms/paper/{id}/publish,/lms/paper/{id}/archive', 'PUT', 1930, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1930 OR perm_code = 'sys:lms:paper:publish');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('lms_admin', 'tenant_admin') AND p.id BETWEEN 1922 AND 1930) OR (r.role_code = 'lms_lecturer' AND p.id IN (1922, 1923, 1924, 1926, 1927, 1928)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 453（题库）/ 454（试卷），挂在 450 学习培训下
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 453, 1, 450, '题库', '/lms/question', 'lms/question/index', 'database', 453, 'MENU', 'sys:lms:question:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 453);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 454, 1, 450, '试卷', '/lms/paper', 'lms/paper/index', 'file-text', 454, 'MENU', 'sys:lms:paper:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 454);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1922 AND 1930 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('lms_admin','lms_lecturer','lms_member','tenant_admin')
--   GROUP BY r.role_code;
--   -- 期望：lms_admin/tenant_admin ≥9、lms_lecturer ≥6、lms_member 0（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id IN (453, 454);
-- =====================================================================
