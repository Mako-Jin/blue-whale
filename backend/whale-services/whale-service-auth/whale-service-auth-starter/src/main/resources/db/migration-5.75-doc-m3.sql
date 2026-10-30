-- =====================================================================
-- Wave 5.75 M3：在线文档 / Wiki 服务（whale-doc）M3 权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.75-doc-m3.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : M3 新增模板、复制、导入导出、发布审批、登录内分享与归档。
-- 权限编号 : 251–270 为 M1，本脚本使用 271–274。
-- 菜单编号 : 已用到 37（文档空间），本脚本新增 38（文档模板）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（271–274）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 271, 0, 'sys:doc:template:manage', '管理文档模板', 'API', '/doc/template,/doc/template/{id}', 'POST,PUT,DELETE', 271, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 271 OR perm_code = 'sys:doc:template:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 272, 0, 'sys:doc:share:manage', '管理文档分享', 'API', '/doc/page/{id}/share,/doc/page/{id}/share/{shareId}', 'GET,POST,DELETE', 272, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 272 OR perm_code = 'sys:doc:share:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 273, 0, 'sys:doc:export', '导出文档页面', 'API', '/doc/page/{id}/export', 'GET', 273, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 273 OR perm_code = 'sys:doc:export');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 274, 0, 'sys:doc:import', '导入文档页面', 'API', '/doc/page/{id}/import', 'POST', 274, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 274 OR perm_code = 'sys:doc:import');

-- ---------------------------------------------------------------------
-- 2. 授权：doc_manager / doc_member / tenant_admin 获得 271–274
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 271 AND 274 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('doc_manager', 'doc_member', 'tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单：文档模板（38）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 38, 1, 36, '文档模板', '/doc/templates', 'doc/templates', 'copy', 2, 'MENU', 'sys:doc:template:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 38);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 271 AND 274;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 271 AND 274 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 38;
-- =====================================================================
