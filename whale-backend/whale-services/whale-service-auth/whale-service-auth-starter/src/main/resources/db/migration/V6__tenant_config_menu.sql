-- =============================================================================
-- 文件        : V6__tenant_config_menu.sql
-- 说明        : Wave 6.19 —— 租户配置页菜单（权限码 sys:tenant:config:list 已由 V4 种子）
--
-- 背景：V4 只种子了权限码 2322/2323（sys:tenant:config:list/edit）并授予 tenant_admin，
--       但没有对应菜单行，管理员无入口；前端 admin 契约键 tenant/config 已就绪。
--
-- 本脚本（幂等）：
--   1. 平台租户（tenant_id = 1）新增菜单 37「租户配置」→ /system/tenant-config；
--   2. 复制到已有业务租户（父节点取各租户自己的 /system 目录）。
--
-- 主键沿用 V3 的 MD5 派生规则，重复执行不会产生第二行。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 平台租户菜单模板
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT 37, 1, 1, '租户配置', '/system/tenant-config', 'tenant/config/index', 'setting', 10,
       'MENU', 'sys:tenant:config:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 37);

-- ---------------------------------------------------------------------
-- 2. 复制到已有业务租户（父级取本租户的 /system 目录）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:tenant-config')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), m.name, m.path, m.component, m.icon, m.sort,
       m.menu_type, m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.id = 37 AND m.deleted = 0
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = m.path AND x.deleted = 0
  );
