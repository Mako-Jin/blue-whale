-- =====================================================================
-- Wave 5.72：管理后台前端菜单（bluewhale_auth）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.72-admin-frontend-menus.sql
-- 幂等说明 : 菜单为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : 前端 admin 已实现「消息模板管理」（权限 239–242）与
--            「支付单管理」（权限 70–73、244–245）页面，本迁移只补菜单入口。
-- 菜单编号 : 已用到 32（ERP 供应商），本脚本从 33 起分配。
-- 可见性   : /auth/me 按用户权限码过滤菜单（menu.permission_code 命中才返回），
--            无需额外的菜单-角色授权表；tenant_admin 已在 5.70/5.71 获得对应权限。
-- =====================================================================

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 33, 1, 0, '消息模板管理', '/message/template', 'message/template', 'notification', 33, 'MENU', 'sys:message:template:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 33);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 34, 1, 0, '支付单管理', '/pay/order', 'pay/order', 'money-collect', 34, 'MENU', 'sys:pay:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 34);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id IN (33, 34);
--   -- 以 admin 登录后 GET /auth/me，确认 menus 树包含上述两项
-- =====================================================================
