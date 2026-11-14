-- =============================================================================
-- 文件        : V29__auth_admin_pages.sql
-- 说明        : auth 扩展功能管理端补齐——登录历史权限码修正 + OAuth 客户端菜单
--
-- 背景：
--   1. V22 种子把登录历史权限码写成 sys:loginhistory:list/export，而
--      LoginHistoryController 的 @RequirePermission 是 sys:login:history:list/export
--      （AUTH-04：权限点必须与注解一一对应）。持有种子码的租户管理员全部 403。
--      本脚本就地改码（perm_code 是判重键、sys_role_permission 引用 permission_id，
--      改码可保留既有授权），并同步修正菜单 /system/login-history 的 permission_code。
--   2. V12 OAuth 客户端只有权限点没有菜单种子，管理端无处可进。本脚本补
--      「OAuth 客户端」菜单（/system/oauth-client，权限码 sys:oauth:client:list）。
--
-- 边界：
--   - 菜单判重键 = (tenant_id, path)，主键 MD5 派生，可重复执行；
--   - OAuth 客户端菜单复制到存量业务租户（V19 模式）；V12 已授予 tenant_admin，
--     不再重复授权段。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 登录历史权限码修正（改码保留 id 与既有授权）
-- ---------------------------------------------------------------------
UPDATE sys_permission
SET perm_code = 'sys:login:history:list',
    perm_name = '查询登录历史'
WHERE perm_code = 'sys:loginhistory:list' AND deleted = 0;

UPDATE sys_permission
SET perm_code = 'sys:login:history:export',
    perm_name = '导出登录历史'
WHERE perm_code = 'sys:loginhistory:export' AND deleted = 0;

UPDATE sys_menu
SET permission_code = 'sys:login:history:list'
WHERE path = '/system/login-history' AND deleted = 0
  AND permission_code = 'sys:loginhistory:list';

-- ---------------------------------------------------------------------
-- 2. 平台租户「OAuth 客户端」菜单（主键 MD5 派生）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:oauth-client'), 1, 15), 16, 10) AS UNSIGNED),
       1,
       COALESCE((SELECT id FROM sys_menu
                 WHERE tenant_id = 1 AND path = '/system' AND menu_type = 'DIR' AND deleted = 0
                 LIMIT 1), 0),
       'OAuth 客户端', '/system/oauth-client', 'system/oauth-client/index', 'api', 19,
       'MENU', 'sys:oauth:client:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/oauth-client' AND deleted = 0
);

-- ---------------------------------------------------------------------
-- 3. 存量业务租户复制菜单（V19 模式）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:oauth-client')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), 'OAuth 客户端', '/system/oauth-client',
       'system/oauth-client/index', 'api', 19,
       'MENU', 'sys:oauth:client:list', 1, 1
FROM sys_tenant t
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = '/system/oauth-client' AND x.deleted = 0
  );
