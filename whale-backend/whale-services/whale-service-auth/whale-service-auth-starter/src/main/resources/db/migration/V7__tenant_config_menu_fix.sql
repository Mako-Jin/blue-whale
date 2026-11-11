-- =============================================================================
-- 文件        : V7__tenant_config_menu_fix.sql
-- 说明        : 修正 V6 —— 菜单主键撞号导致的静默跳过（Wave 6.19 收口）
--
-- 事故复盘：
--   V6 用固定 id = 37 插入「租户配置」菜单，但 37 早已被 whale-doc 的
--   /doc/spaces 菜单占用（sys_menu.id 非自增，各服务迁移各自手工编号）。
--   结果是：① 平台租户那一条被 `NOT EXISTS(id=37)` 静默跳过，页面无入口；
--   ② 更危险的是第二段「复制到业务租户」按 id=37 取模板，取到的是 doc 菜单，
--      若非本环境只有 1 个租户，就会给每个租户复制一份重复的 /doc/spaces。
--
-- 本脚本：
--   1. 清理 V6 可能误复制的 doc 菜单（谓词收紧到「id 恰为 V6 派生值且 path=/doc/spaces」，
--      不会误伤合法 doc 菜单——它们的 id 由各自迁移决定，不会等于该派生值）；
--   2. 用 MD5 派生主键重新插入平台租户菜单，**判重键改为 (tenant_id, path)**；
--   3. 同样按路径判重复制到业务租户。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 清理 V6 误复制的产物（幂等：无匹配即 0 行）
-- ---------------------------------------------------------------------
DELETE FROM sys_menu
WHERE path = '/doc/spaces'
  AND id <> 37
  AND id = CAST(CONV(SUBSTR(MD5(CONCAT(tenant_id, ':menu:tenant-config')), 1, 15), 16, 10) AS UNSIGNED);

-- ---------------------------------------------------------------------
-- 2. 平台租户菜单（判重键：tenant_id + path）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:tenant-config'), 1, 15), 16, 10) AS UNSIGNED),
       1, 1, '租户配置', '/system/tenant-config', 'tenant/config/index', 'setting', 10,
       'MENU', 'sys:tenant:config:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/tenant-config' AND deleted = 0
);

-- ---------------------------------------------------------------------
-- 3. 复制到已有业务租户（父级取本租户 /system 目录，按路径判重）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:tenant-config-page')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), m.name, m.path, m.component, m.icon, m.sort,
       m.menu_type, m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.path = '/system/tenant-config' AND m.deleted = 0
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = m.path AND x.deleted = 0
  );
