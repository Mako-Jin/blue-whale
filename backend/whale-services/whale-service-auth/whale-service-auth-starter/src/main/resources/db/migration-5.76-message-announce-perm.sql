-- =====================================================================
-- whale-message：系统公告管理权限（bluewhale_auth）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.76-message-announce-perm.sql
-- 幂等说明 : 权限与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 权限编号 : 304 已被 sys:music:upload 占用，本脚本从 305 起分配。
-- 菜单编号 : 已用到 34（支付单管理），本脚本分配 35。
-- 授权口径 : tenant_admin 获得公告管理权限；super_admin 由系统内置全量权限覆盖。
-- 收件箱   : /message/inapp/** 是登录用户自助能力，公告展开发生在收件箱读取链路，不分配权限码。
-- 菜单可见 : /auth/me 按用户权限码过滤菜单（permission_code 命中才返回），无需额外授权表。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 305, 0, 'sys:message:announce:list', '查询系统公告', 'API', '/message/announce/page', 'GET', 305, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 305 OR perm_code = 'sys:message:announce:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 306, 0, 'sys:message:announce:publish', '发布系统公告', 'API', '/message/announce', 'POST', 306, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 306 OR perm_code = 'sys:message:announce:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 307, 0, 'sys:message:announce:revoke', '撤回系统公告', 'API', '/message/announce/{id}/revoke', 'PUT', 307, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 307 OR perm_code = 'sys:message:announce:revoke');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 308, 0, 'sys:message:announce:delete', '删除系统公告', 'API', '/message/announce/{id}', 'DELETE', 308, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 308 OR perm_code = 'sys:message:announce:delete');

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (305, 306, 307, 308) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 管理后台菜单入口（可见性由 permission_code 决定，不额外授权菜单角色）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 35, 1, 0, '系统公告', '/message/announce', 'message/announce', 'notification', 35, 'MENU', 'sys:message:announce:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 35);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 305 AND 308;
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 35;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 305 AND 308 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 以 admin 登录后 GET /auth/me，确认 menus 树包含「系统公告」
-- =====================================================================
