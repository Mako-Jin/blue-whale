-- =====================================================================
-- Wave 5.87 M3-2：学习培训考试（whale-lms）考试与判分权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-lms-m3b.sql
-- 幂等说明 : 权限、菜单、授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-lms M3-2 考试（考试/作答记录/作答明细/成绩版本），
--            含学员作答三件套与教师判分两件套。
-- 权限编号 : LMS 段 1900–1949；M3-1 已用 1922–1930，本脚本使用 1931–1940；
--            M4 证书学分从 1944 起编。
-- 菜单编号 : LMS 段 450–469；本脚本使用 455（考试）、456（判分）。
-- 授权口径 : lms_admin、tenant_admin 获得 1931–1940 全量；
--            lms_lecturer 获得 1931/1939/1940（看考试、看待判与判分，不含建考与发布）；
--            lms_member 获得 1931/1936/1937/1938（看考试列表 + 开始/保存/交卷）。
-- 前置条件 : bluewhale_lms 已由 Flyway V5 建表；migration-5.87-lms-m3a.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1931–1940
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1931, 0, 'sys:lms:exam:list', '查询考试', 'API', '/lms/exam/page,/lms/exam/{id}', 'GET', 1931, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1931 OR perm_code = 'sys:lms:exam:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1932, 0, 'sys:lms:exam:create', '新建考试', 'API', '/lms/exam', 'POST', 1932, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1932 OR perm_code = 'sys:lms:exam:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1933, 0, 'sys:lms:exam:update', '编辑考试', 'API', '/lms/exam/{id}', 'PUT', 1933, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1933 OR perm_code = 'sys:lms:exam:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1934, 0, 'sys:lms:exam:delete', '删除考试', 'API', '/lms/exam/{id}', 'DELETE', 1934, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1934 OR perm_code = 'sys:lms:exam:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1935, 0, 'sys:lms:exam:publish', '发布与结课考试', 'API', '/lms/exam/{id}/publish,/lms/exam/{id}/close', 'PUT', 1935, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1935 OR perm_code = 'sys:lms:exam:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1936, 0, 'sys:lms:attempt:start', '开始作答与查看卷面', 'API', '/lms/attempt/start/{examId},/lms/attempt/{attemptId}/paper', 'POST,GET', 1936, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1936 OR perm_code = 'sys:lms:attempt:start');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1937, 0, 'sys:lms:attempt:save', '作答中保存答案', 'API', '/lms/attempt/{attemptId}/answers', 'PUT', 1937, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1937 OR perm_code = 'sys:lms:attempt:save');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1938, 0, 'sys:lms:attempt:submit', '交卷与查询本人成绩', 'API', '/lms/attempt/{attemptId}/submit,/lms/attempt/mine,/lms/attempt/{attemptId}/result,/lms/attempt/scores/mine', 'POST,GET', 1938, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1938 OR perm_code = 'sys:lms:attempt:submit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1939, 0, 'sys:lms:grade:list', '待判队列与判分明细', 'API', '/lms/grade/pending,/lms/grade/{attemptId}', 'GET', 1939, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1939 OR perm_code = 'sys:lms:grade:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1940, 0, 'sys:lms:grade:submit', '提交人工判分', 'API', '/lms/grade/{attemptId}', 'POST', 1940, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1940 OR perm_code = 'sys:lms:grade:submit');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('lms_admin', 'tenant_admin') AND p.id BETWEEN 1931 AND 1940) OR (r.role_code = 'lms_lecturer' AND p.id IN (1931, 1939, 1940)) OR (r.role_code = 'lms_member' AND p.id IN (1931, 1936, 1937, 1938)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 455（考试）/ 456（判分），挂在 450 学习培训下
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 455, 1, 450, '考试', '/lms/exam', 'lms/exam/index', 'edit', 455, 'MENU', 'sys:lms:exam:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 455);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 456, 1, 450, '判分', '/lms/grade', 'lms/grade/index', 'check-square', 456, 'MENU', 'sys:lms:grade:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 456);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1931 AND 1940 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('lms_admin','lms_lecturer','lms_member','tenant_admin')
--   GROUP BY r.role_code;
--   -- 期望：lms_admin/tenant_admin ≥10、lms_lecturer ≥3、lms_member ≥4（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id IN (455, 456);
-- =====================================================================
