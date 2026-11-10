-- =====================================================================
-- Wave 6.01 — AI 助手平台（whale-assistant）权限码与菜单注册
-- 目标库：bluewhale_auth（手工增量脚本，幂等，可重复执行）
--
-- 内容：
--   sys:assistant:define / sys:assistant:chat 权限（2300–2301）、
--   tenant_admin 授权与菜单入口（菜单 510–511）。
--
-- 可见性机制（与 5.73 同口径）：
--   /auth/me 按 sys_menu.permission_code 命中用户权限码才返回菜单，
--   无需额外的菜单-角色授权表；tenant_admin 由下方授权语句获得权限。
--
-- 验证 SQL：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 2300 AND 2301;
--   SELECT id, name, path, component FROM sys_menu WHERE id BETWEEN 510 AND 511;
-- =====================================================================

-- 1) 权限码 -------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2300, 0, 'sys:assistant:define', 'AI 助手管理', 'API', '/assistant/definitions/**', NULL, 100, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2300 OR perm_code = 'sys:assistant:define');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2301, 0, 'sys:assistant:chat', 'AI 助手问答', 'API', '/assistant/sessions/**', NULL, 101, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2301 OR perm_code = 'sys:assistant:chat');

-- 2) tenant_admin 授权 --------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (2300, 2301) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id);

-- 3) 菜单 ---------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 510, 1, 0, '助手管理', '/assistant/definition', 'assistant/definition/index', 'robot', 60, 'MENU', 'sys:assistant:define', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 510);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 511, 1, 0, '助手用量', '/assistant/usage', 'assistant/usage/index', 'bar-chart', 61, 'MENU', 'sys:assistant:define', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 511);
