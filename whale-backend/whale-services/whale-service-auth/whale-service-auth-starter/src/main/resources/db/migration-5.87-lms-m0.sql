-- =====================================================================
-- Wave 5.87 M0：学习培训考试（whale-lms）权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-lms-m0.sql
-- 幂等说明 : 权限、菜单、角色与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-lms 新建（M0 底座 + M1 课程与学习：课程/章节/课件 CRUD、
--            发布归档、报名幂等、学习进度只增不减）。
-- 权限编号 : LMS 段 1900–1949；本脚本使用 1900–1910。
-- 菜单编号 : LMS 段 450–469；本脚本使用 450（根菜单）与 451（课程管理）。
-- 角色编号 : 25133–25135（lms_admin / lms_lecturer / lms_member）。
-- 授权口径 : lms_admin、tenant_admin 获得 1900–1910 全量；
--            lms_lecturer 获得 1900–1907（课程与内容管理，不含删除与学习端）；
--            lms_member 获得 1900/1908/1909/1910（浏览、我的学习、报名、报进度）。
-- 前置条件 : bluewhale_lms 已由 Flyway V1/V2 建表；网关已配 /lms/** 路由。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1900–1910
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1900, 0, 'sys:lms:browse', '学习门禁状态查询', 'API', '/lms/meta', 'GET', 1900, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1900 OR perm_code = 'sys:lms:browse');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1901, 0, 'sys:lms:course:list', '查询课程', 'API', '/lms/course/page,/lms/course/{id}', 'GET', 1901, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1901 OR perm_code = 'sys:lms:course:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1902, 0, 'sys:lms:course:create', '新建课程', 'API', '/lms/course', 'POST', 1902, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1902 OR perm_code = 'sys:lms:course:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1903, 0, 'sys:lms:course:update', '编辑课程', 'API', '/lms/course/{id}', 'PUT', 1903, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1903 OR perm_code = 'sys:lms:course:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1904, 0, 'sys:lms:course:status', '课程发布/归档', 'API', '/lms/course/{id}/status', 'PUT', 1904, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1904 OR perm_code = 'sys:lms:course:status');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1905, 0, 'sys:lms:course:delete', '删除课程', 'API', '/lms/course/{id}', 'DELETE', 1905, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1905 OR perm_code = 'sys:lms:course:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1906, 0, 'sys:lms:chapter:manage', '章节管理', 'API', '/lms/course/{courseId}/chapter,/lms/course/chapter/{chapterId}', 'POST,DELETE', 1906, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1906 OR perm_code = 'sys:lms:chapter:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1907, 0, 'sys:lms:ware:manage', '课件管理', 'API', '/lms/course/chapter/{chapterId}/ware,/lms/course/ware/{wareId}', 'POST,DELETE', 1907, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1907 OR perm_code = 'sys:lms:ware:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1908, 0, 'sys:lms:learning:list', '我的学习查询', 'API', '/lms/learning/my', 'GET', 1908, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1908 OR perm_code = 'sys:lms:learning:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1909, 0, 'sys:lms:learning:enroll', '报名课程', 'API', '/lms/learning/enroll/{courseId}', 'POST', 1909, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1909 OR perm_code = 'sys:lms:learning:enroll');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1910, 0, 'sys:lms:learning:progress', '上报学习进度', 'API', '/lms/learning/{courseId}/progress', 'POST', 1910, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1910 OR perm_code = 'sys:lms:learning:progress');

-- ---------------------------------------------------------------------
-- 2. 角色 25133–25135（平台租户固定 ID + 业务租户按 MD5 派生）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25133, 1, 'lms_admin', '学习管理员', '课程/培训/考试全部功能与配置', 253, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25133 OR (tenant_id = 1 AND role_code = 'lms_admin'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25134, 1, 'lms_lecturer', '讲师', '课程与内容维护，不含删除', 254, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25134 OR (tenant_id = 1 AND role_code = 'lms_lecturer'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25135, 1, 'lms_member', '学员', '浏览课程、报名与学习进度', 255, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25135 OR (tenant_id = 1 AND role_code = 'lms_member'));

INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':lms_admin')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'lms_admin' AS role_code, '学习管理员' AS role_name, '课程/培训/考试全部功能与配置' AS description, 253 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':lms_lecturer')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'lms_lecturer' AS role_code, '讲师' AS role_name, '课程与内容维护，不含删除' AS description, 254 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':lms_member')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'lms_member' AS role_code, '学员' AS role_name, '浏览课程、报名与学习进度' AS description, 255 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 3. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('lms_admin', 'tenant_admin') AND p.id BETWEEN 1900 AND 1910) OR (r.role_code = 'lms_lecturer' AND p.id BETWEEN 1900 AND 1907) OR (r.role_code = 'lms_member' AND p.id IN (1900, 1901, 1908, 1909, 1910)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 4. 菜单 450（学习培训根菜单）/ 451（课程管理）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 450, 1, 0, '学习培训', '/lms', 'lms/index', 'read', 450, 'MENU', 'sys:lms:browse', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 450);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 451, 1, 450, '课程管理', '/lms/course', 'lms/course/index', 'book', 451, 'MENU', 'sys:lms:course:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 451);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1900 AND 1910 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('lms_admin','lms_lecturer','lms_member','tenant_admin')
--   GROUP BY r.role_code;
--   -- 期望：lms_admin/tenant_admin ≥11、lms_lecturer ≥8、lms_member ≥5（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id IN (450, 451);
-- =====================================================================
