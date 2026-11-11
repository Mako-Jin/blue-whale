-- =============================================================================
-- 文件        : V9__tenant_config_menu_dedupe.sql
-- 说明        : 清理 V6 固定 id=37 留下的重复「租户配置」菜单
--
-- 事故复盘：见 V7__tenant_config_menu_fix.sql 头部。
--   V6 用固定 id = 37 插入 /system/tenant-config，而 37 是 whale-doc 的
--   /doc/spaces 菜单号（历史手工脚本 migration-5.74-doc.sql）。V6 已由 Flyway
--   执行且不得修改（validate-on-migrate=true），因此只能在此收尾：
--   · 有 doc 菜单的库：V6 那两条 INSERT 本来就被跳过，本脚本无匹配（0 行）；
--   · Flyway 新建的空库：V1 基线没有 doc 菜单，37 空闲 → V6 写入一条，
--     V7 又按 (tenant_id, path) 写入一条，同一菜单出现两行，前端菜单树重复。
--
-- 本脚本：删除「id=37 且 path=/system/tenant-config」的重复行，
--   仅在存在同 (tenant_id, path) 的另一行时删除；幂等，可重复执行。
-- =============================================================================

DELETE m
FROM sys_menu m
JOIN sys_menu keep
  ON keep.tenant_id = m.tenant_id
 AND keep.path = m.path
 AND keep.id <> m.id
 AND keep.deleted = 0
WHERE m.id = 37
  AND m.path = '/system/tenant-config'
  AND m.deleted = 0;

-- ---------------------------------------------------------------------
-- 校验：
--   SELECT tenant_id, path, COUNT(*) FROM sys_menu
--    WHERE path = '/system/tenant-config' AND deleted = 0 GROUP BY tenant_id, path;
--     期望：每个租户 1 行
-- =====================================================================
