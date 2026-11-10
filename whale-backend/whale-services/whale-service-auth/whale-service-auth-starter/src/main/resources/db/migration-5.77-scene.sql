-- =====================================================================
-- Wave 5.77 M0：城池场景导航服务（whale-scene）权限、角色与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.77-scene.sql
-- 幂等说明 : 权限、角色、授权与菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除既有数据。
-- 背景     : 2D 城池导航为主生产力界面，3D 为可选增强层（M3）；
--            导航接口登录即可访问，管理类接口按权限码控制；
--            跨租户一律按不存在处理。
-- 权限编号 : 已用至 322（视频），本脚本使用 323–330（8 项），331–336 预留。
-- 菜单编号 : 已用至 52（视频），本脚本使用 53–56。
-- 角色编号 : 平台角色 25107 scene_admin（场景管理员）；
--            业务租户复制角色与授权 ID 采用「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : scene_admin / tenant_admin 获得 323–330（8 项）；
--            普通登录用户只看到「城池导航」菜单（无权限码，导航接口登录即可）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（323–330）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 323, 0, 'sys:scene:list', '查看场景管理数据', 'API', '/scene/page,/scene/{id}', 'GET', 323, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 323 OR perm_code = 'sys:scene:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 324, 0, 'sys:scene:add', '新建场景', 'API', '/scene', 'POST', 324, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 324 OR perm_code = 'sys:scene:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 325, 0, 'sys:scene:edit', '编辑场景', 'API', '/scene/{id}', 'PUT', 325, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 325 OR perm_code = 'sys:scene:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 326, 0, 'sys:scene:publish', '发布场景', 'API', '/scene/{id}/publish', 'POST', 326, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 326 OR perm_code = 'sys:scene:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 327, 0, 'sys:scene:offline', '下线场景', 'API', '/scene/{id}/offline', 'POST', 327, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 327 OR perm_code = 'sys:scene:offline');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 328, 0, 'sys:scene:node:manage', '管理场景节点', 'API', '/scene/{id}/node,/scene/{id}/node/{nodeId},/scene/node/{nodeId}', 'ALL', 328, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 328 OR perm_code = 'sys:scene:node:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 329, 0, 'sys:scene:binding:manage', '管理节点入口绑定', 'API', '/scene/node/{nodeId}/binding', 'PUT', 329, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 329 OR perm_code = 'sys:scene:binding:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 330, 0, 'sys:scene:visibility:manage', '管理场景可见性', 'API', '/scene/{id}/visibility', 'PUT', 330, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 330 OR perm_code = 'sys:scene:visibility:manage');

-- ---------------------------------------------------------------------
-- 2. 平台租户业务角色模板（25107 场景管理员）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25107, 1, 'scene_admin', '场景管理员', '城池场景/节点/入口绑定/可见性/发布与下线全量管理入口（2D 优先，3D 为可选增强）', 257, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25107 OR (tenant_id = 1 AND role_code = 'scene_admin'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制场景角色（仅 tenant_type='TENANT' 的启用租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', 'scene_admin')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'scene_admin' AS role_code, '场景管理员' AS role_name, '城池场景/节点/入口绑定/可见性/发布与下线全量管理入口（2D 优先，3D 为可选增强）' AS description, 257 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与全部业务租户）
--    scene_admin / tenant_admin：323–330（8 项）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 323 AND 330 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('scene_admin', 'tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单：城池导航（53，登录可见）与场景管理子菜单（54–56）
--    可见性由 /auth/me 按用户权限码过滤，无需菜单-角色授权表。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 53, 1, 0, '城池导航', '/scene', NULL, 'crown', 53, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 53);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 54, 1, 53, '场景管理', '/scene/manage', 'scene/manage', 'appstore', 1, 'MENU', 'sys:scene:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 54);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 55, 1, 53, '入口绑定', '/scene/bindings', 'scene/bindings', 'link', 2, 'MENU', 'sys:scene:binding:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 55);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 56, 1, 53, '可见性', '/scene/visibility', 'scene/visibility', 'eye', 3, 'MENU', 'sys:scene:visibility:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 56);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 323 AND 330;
--   SELECT tenant_id, role_code FROM sys_role WHERE role_code = 'scene_admin' ORDER BY tenant_id;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 323 AND 330 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：scene_admin / tenant_admin 各 8 项
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 53 AND 56;
-- =====================================================================
