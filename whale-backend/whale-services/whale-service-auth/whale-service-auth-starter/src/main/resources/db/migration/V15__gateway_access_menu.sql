-- =============================================================================
-- 文件        : V15__gateway_access_menu.sql
-- 说明        : 网关访问名单管理端菜单（白名单 / 黑名单）
--
-- 背景：V13/V14 已把白名单与黑名单落到 auth 库，并预留了管理端 CRUD 接口与
--       权限码（2360–2367），但未建菜单，管理页面无法从侧边栏进入。本脚本补一个
--       平台租户菜单节点，指向管理端「网关访问名单」页（白/黑两个 Tab）。
--
-- 边界：
--   1. 仅平台租户（tenant_id = 1）可见——名单是平台级控制平面数据，
--      业务租户管理员既没有对应权限码，也不应看到入口；
--   2. 不授予 tenant_admin：权限码维持只对内置超管（super_admin）开放；
--   3. 菜单判重键 = (tenant_id, path)，主键由 MD5 派生，可重复执行。
-- =============================================================================

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:gateway-access'), 1, 15), 16, 10) AS UNSIGNED),
       1, 1, '网关访问名单', '/system/gateway-access', 'gateway/access/index',
       'safety-certificate', 8, 'MENU', 'sys:gateway:whitelist:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE tenant_id = 1 AND path = '/system/gateway-access' AND deleted = 0
);
