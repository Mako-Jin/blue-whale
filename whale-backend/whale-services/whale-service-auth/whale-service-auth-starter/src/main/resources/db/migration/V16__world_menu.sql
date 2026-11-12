-- =============================================================================
-- 文件        : V16__world_menu.sql
-- 说明        : Wave 7.01-P0 —— 蓝鲸城 3D（whale-world）入口权限与菜单
--
-- 背景：
--   whale-world 是独立 3D 子应用（前端端口 8124，activeRule /world），对既有
--   2D 子应用零侵入：不调用任何后端接口、不改既有路由。基座菜单项
--   「蓝鲸城 3D」引用权限码 sys:world:browse，本脚本为其落权限与菜单种子，
--   使 check-frontend-perm-mapping 门禁通过、授权角色可见入口。
--
-- 本脚本（幂等，判重键不用固定 id）：
--   1. 号段 2380 插入权限（判重键 = perm_code）；
--   2. 平台租户（tenant_id = 1）插入菜单 /world（主键 MD5 派生）；
--   3. 复制菜单到存量业务租户（V5 模式，按 (tenant_id, path) 判重）；
--   4. 按 perm_code（不按 id 区间，见 2026-11-09 撞号事故）授予各租户
--      tenant_admin。
--
-- 号段登记：2380 = whale-world（sys:world:browse；2368–2379 留在途批次）。
-- 未执行说明：随 auth 服务下次起服由 Flyway 自动应用；在此之前 /world 仅可
-- URL 直达，菜单不显示（前端 permission 过滤），对 2D 用户无任何影响。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（判重键：perm_code）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2380, 0, 'sys:world:browse', '浏览蓝鲸城 3D 主场景（/world 子应用入口）', 'MENU', '/world', 'GET', 2380, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:world:browse' AND deleted = 0);

-- ---------------------------------------------------------------------
-- 2. 平台租户菜单 /world（主键 MD5 派生，判重键 (tenant_id, path)）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(1, ':menu:world')), 1, 15), 16, 10) AS UNSIGNED),
       1, 0, '蓝鲸城 3D', '/world', 'world/index', 'global', 2, 'MENU', 'sys:world:browse', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND path = '/world' AND deleted = 0
);

-- ---------------------------------------------------------------------
-- 3. 存量业务租户复制菜单（父级为根；新租户由租户开通服务复制平台模板）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:world')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, 0, m.name, m.path, m.component, m.icon, m.sort,
       m.menu_type, m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.path = '/world' AND m.menu_type = 'MENU' AND m.deleted = 0
WHERE t.id <> 1 AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = t.id AND x.path = m.path AND x.deleted = 0);

-- ---------------------------------------------------------------------
-- 4. 授权：各租户 tenant_admin 按 perm_code 授予（超管由 PermissionInterceptor 放行）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code = 'sys:world:browse' AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
