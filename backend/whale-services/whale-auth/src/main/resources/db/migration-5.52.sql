-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.52：WebIM 权限点）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_permission 表的运行库
--            （保留存量权限与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 1 行）。
-- 说明     : 本脚本幂等（INSERT IGNORE 按主键去重）。
--            · (120) sys:im:chat —— whale-im 全类 @RequirePermission("sys:im:chat")
--            菜单项待前端 whale-im 子应用（8107）落地后再登记，避免指向 404；
--            super_admin 默认拥有全部权限，其余角色由授权界面分配。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (120, 0, 'sys:im:chat', 'IM 会话与消息', 'API', '/im/**', 'GET,POST', 120, 1);
