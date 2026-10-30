-- =====================================================================
-- Wave 5.74 M0：在线文档 / Wiki 服务（whale-doc）权限、角色与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.74-doc.sql
-- 幂等说明 : 权限、角色、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : Wave 5.74 M1 引入 whale-doc（文档空间 / 页面树 / 版本 / 附件 / 评论 /
--            搜索 / 收藏）。平台权限码只控制功能入口，数据可见性由 DocAccessGuard
--            按空间成员角色（OWNER > ADMIN > EDITOR > READER）二次校验。
-- 权限编号 : 250 已用到 250（config 246–250），本脚本使用 251–270（M1）；
--            271–277 预留给 M3 模板/分享/导入导出/审批与 M4 文档 AI / 跨空间兜底。
-- 菜单编号 : 已用到 35（业务配置 35），本脚本从 36 起分配。
-- 角色编号 : 平台角色沿用 25101 / 25102；业务租户复制角色与授权 ID 采用
--            「业务键 MD5 前 15 位」，与 5.65/5.66 一致，重复执行 ID 稳定。
-- 授权口径 : doc_manager / doc_member / tenant_admin 均获得 251–270；
--            M1 两者功能相同，doc_manager 作为文档管理员标记，M4 再增 sys:doc:admin。
-- 注意     : doc_member 不默认授予全租户用户，由租户管理员按组织范围授予（见文末示例）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（251–270，M1）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 251, 0, 'sys:doc:space:list', '查询文档空间', 'API', '/doc/space/page,/doc/space/{id}', 'GET', 251, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 251 OR perm_code = 'sys:doc:space:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 252, 0, 'sys:doc:space:add', '创建文档空间', 'API', '/doc/space', 'POST', 252, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 252 OR perm_code = 'sys:doc:space:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 253, 0, 'sys:doc:space:manage', '管理文档空间', 'API', '/doc/space/{id},/doc/space/{id}/archive', 'PUT', 253, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 253 OR perm_code = 'sys:doc:space:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 254, 0, 'sys:doc:member:list', '查询空间成员', 'API', '/doc/space/{id}/member', 'GET', 254, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 254 OR perm_code = 'sys:doc:member:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 255, 0, 'sys:doc:member:manage', '管理空间成员', 'API', '/doc/space/{id}/member,/doc/space/{id}/member/{userId}', 'POST,DELETE', 255, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 255 OR perm_code = 'sys:doc:member:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 256, 0, 'sys:doc:page:list', '查询页面树', 'API', '/doc/space/{id}/page/tree', 'GET', 256, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 256 OR perm_code = 'sys:doc:page:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 257, 0, 'sys:doc:page:read', '读取页面', 'API', '/doc/page/{id},/doc/page/by-slug', 'GET', 257, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 257 OR perm_code = 'sys:doc:page:read');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 258, 0, 'sys:doc:page:add', '新建页面', 'API', '/doc/page', 'POST', 258, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 258 OR perm_code = 'sys:doc:page:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 259, 0, 'sys:doc:page:edit', '编辑页面', 'API', '/doc/page/{id},/doc/page/{id}/title', 'PUT', 259, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 259 OR perm_code = 'sys:doc:page:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 260, 0, 'sys:doc:page:delete', '删除页面', 'API', '/doc/page/{id}', 'DELETE', 260, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 260 OR perm_code = 'sys:doc:page:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 261, 0, 'sys:doc:page:move', '移动页面', 'API', '/doc/page/{id}/move', 'POST', 261, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 261 OR perm_code = 'sys:doc:page:move');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 262, 0, 'sys:doc:page:publish', '发布页面', 'API', '/doc/page/{id}/publish', 'POST', 262, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 262 OR perm_code = 'sys:doc:page:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 263, 0, 'sys:doc:page:archive', '归档/恢复页面', 'API', '/doc/page/{id}/archive,/doc/page/{id}/restore', 'POST', 263, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 263 OR perm_code = 'sys:doc:page:archive');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 264, 0, 'sys:doc:version:list', '查询版本历史', 'API', '/doc/page/{id}/version/page,/doc/page/{id}/version/{revision}', 'GET', 264, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 264 OR perm_code = 'sys:doc:version:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 265, 0, 'sys:doc:version:restore', '恢复历史版本', 'API', '/doc/page/{id}/version/{revision}/restore', 'POST', 265, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 265 OR perm_code = 'sys:doc:version:restore');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 266, 0, 'sys:doc:attachment:manage', '管理页面附件', 'API', '/doc/page/{id}/attachment,/doc/page/{id}/attachment/{attachmentId}', 'POST,DELETE', 266, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 266 OR perm_code = 'sys:doc:attachment:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 267, 0, 'sys:doc:comment:list', '查询页面评论', 'API', '/doc/page/{id}/comment', 'GET', 267, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 267 OR perm_code = 'sys:doc:comment:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 268, 0, 'sys:doc:comment:add', '新增页面评论', 'API', '/doc/page/{id}/comment', 'POST', 268, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 268 OR perm_code = 'sys:doc:comment:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 269, 0, 'sys:doc:comment:manage', '管理页面评论', 'API', '/doc/comment/{id}/resolve,/doc/comment/{id}', 'PUT,DELETE', 269, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 269 OR perm_code = 'sys:doc:comment:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 270, 0, 'sys:doc:search', '搜索文档', 'API', '/doc/search', 'GET', 270, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 270 OR perm_code = 'sys:doc:search');

-- ---------------------------------------------------------------------
-- 2. 平台租户业务角色模板（25101 / 25102）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25101, 1, 'doc_manager', '文档管理员', '文档空间/页面/版本/附件/评论全量功能入口，跨空间兜底在 M4 追加', 251, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25101 OR (tenant_id = 1 AND role_code = 'doc_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25102, 1, 'doc_member', '文档成员', '文档功能入口：创建空间成为 OWNER，具体编辑/发布/删除由空间角色二次校验', 252, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25102 OR (tenant_id = 1 AND role_code = 'doc_member'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制文档角色
--    仅复制 tenant_type='TENANT' 的未删除启用租户（排除平台租户）；
--    已存在同名角色（uk_tenant_role_code）时跳过，不覆盖人工调整。
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'doc_manager' AS role_code, '文档管理员' AS role_name, '文档空间/页面/版本/附件/评论全量功能入口，跨空间兜底在 M4 追加' AS description, 251 AS sort_no UNION ALL SELECT 'doc_member', '文档成员', '文档功能入口：创建空间成为 OWNER，具体编辑/发布/删除由空间角色二次校验', 252 ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与全部业务租户）
--    授权 ID = MD5(role_id + ':' + permission_id) 前 15 位，稳定且幂等；
--    uk_role_permission(role_id, permission_id) 兜底重复插入。
--    权限口径：doc_manager / doc_member / tenant_admin 均为 251–270（20 项）。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 251 AND 270 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('doc_manager', 'doc_member', 'tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单：文档中心（36）/ 文档空间（37）
--    可见性由 /auth/me 按用户权限码过滤，无需菜单-角色授权表。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 36, 1, 0, '文档中心', '/doc', NULL, 'file-search', 36, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 36);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 37, 1, 36, '文档空间', '/doc/spaces', 'doc/spaces', 'read', 1, 'MENU', 'sys:doc:space:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 37);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 251 AND 270;
--   SELECT tenant_id, role_code FROM sys_role WHERE role_code IN ('doc_manager','doc_member') ORDER BY tenant_id;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 251 AND 270 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：每个平台/业务租户的 doc_manager、doc_member、tenant_admin 各 20 项
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id IN (36, 37);
--   -- 以 admin 登录后 GET /auth/me，确认 menus 树包含「文档中心 → 文档空间」
--
-- doc_member 批量授予示例（按需执行，不默认放开）：
--   NOT EXISTS 幂等插入 INTO sys_user_role (id, user_id, role_id)
--   SELECT CAST(CONV(SUBSTR(MD5(CONCAT(u.id, ':', r.id)), 1, 15), 16, 10) AS UNSIGNED), u.id, r.id
--   FROM sys_user u
--   JOIN sys_role r ON r.tenant_id = u.tenant_id AND r.role_code = 'doc_member'
--                    AND r.deleted = 0 AND r.status = 1
--   WHERE u.deleted = 0 AND u.status = 1
--     AND u.tenant_id = <目标租户ID>
--     AND u.dept_id IN (<目标部门ID列表>);
-- =====================================================================
