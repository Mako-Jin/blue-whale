-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.34：admin 菜单补齐）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_menu 表的运行库
--            （保留存量菜单与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 2 行）。
-- 说明     : 本脚本幂等（NOT EXISTS 幂等插入 按主键去重）。新增「定时任务」「文件管理」
--            两个菜单项，使 admin 动态路由可经 GET /auth/me 的 menus[] 渲染。
--            两行的 permission_code 分别对应已存在的权限点 sys:job:list（id 26）
--            与 sys:file:list（id 34），无需新增权限点。
-- =====================================================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 7, 1, '定时任务', '/system/job', 'system/job/index', 'clock-circle', 6, 'MENU', 'sys:job:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 8, 1, '文件管理', '/system/file', 'system/file/index', 'folder', 7, 'MENU', 'sys:file:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 8);
