-- =============================================================================
-- 文件        : V28__my_file_menu.sql
-- 说明        : 「我的文件」（个人上传列表）菜单种子
--
-- 背景：
--   whale-file 的 GET /file/v1/page 原本按权限二分：无 sys:file:manage 只看
--   本人上传，持有则看全租户；新增 onlyMine 参数后管理员也能切换「只看我的」
--   视图。前端 admin 新增独立页面（契约键 file/mine），本脚本补菜单入口，
--   使「我的文件」可从侧边栏进入。
--
-- 边界：
--   1. 不新增权限点：菜单引用既有 sys:file:list（id 34，tenant_admin 基线
--      已含），无该权限码的用户菜单不渲染（前端 permission 过滤）；
--   2. 菜单判重键 = (tenant_id, path)，主键 MD5 派生，可重复执行；
--   3. 复制到存量业务租户（V16 模式）；新租户由开通流程复制平台模板。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 平台租户菜单 /mine/file（主键 MD5 派生）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(1, ':menu:my-file')), 1, 15), 16, 10) AS UNSIGNED),
       1, 0, '我的文件', '/mine/file', 'file/mine/index', 'folder', 100,
       'MENU', 'sys:file:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND path = '/mine/file' AND deleted = 0
);

-- ---------------------------------------------------------------------
-- 2. 存量业务租户复制菜单（父级为根）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:my-file')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, 0, m.name, m.path, m.component, m.icon, m.sort,
       m.menu_type, m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.path = '/mine/file' AND m.menu_type = 'MENU' AND m.deleted = 0
WHERE t.id <> 1 AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = t.id AND x.path = m.path AND x.deleted = 0);
