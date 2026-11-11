-- =====================================================================
-- Wave 6.01 — AI 助手平台（whale-assistant）权限码与菜单注册
-- 目标库：bluewhale_auth（手工增量脚本，幂等，可重复执行）
--
-- 内容：
--   sys:assistant:define / sys:assistant:chat 权限（2330–2331）、
--   tenant_admin 授权与菜单入口（/assistant/definition、/assistant/usage）。
--
-- 【2026-11-11 订正】原脚本用 id 2300/2301 + 判重 `id = 2300 OR perm_code = ...`，
--   而这两个 id 早已被 migration-5.83-crm-m6-analytics.sql（CRM 分析）占用，
--   结果整条 INSERT 被静默跳过：sys:assistant:* 从未进库，后端 @RequirePermission
--   全量 403、菜单不出现。现改用 2330/2331 且判重键只看 perm_code；
--   授权段按 perm_code 联查（按 id 联查会把 CRM 权限授给 tenant_admin）。
--   存量库由 Flyway V8__assistant_permission_menu.sql 补齐。
--
-- 可见性机制（与 5.73 同口径）：
--   /auth/me 按 sys_menu.permission_code 命中用户权限码才返回菜单，
--   无需额外的菜单-角色授权表；tenant_admin 由下方授权语句获得权限。
--
-- 验证 SQL：
--   SELECT id, perm_code FROM sys_permission WHERE perm_code LIKE 'sys:assistant:%';
--   SELECT id, name, path, component FROM sys_menu WHERE path LIKE '/assistant/%';
-- =====================================================================

-- 1) 权限码 -------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2330, 0, 'sys:assistant:define', 'AI 助手管理', 'API', '/assistant/definitions/**', NULL, 100, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:assistant:define');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2331, 0, 'sys:assistant:chat', 'AI 助手问答', 'API', '/assistant/sessions/**', NULL, 101, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:assistant:chat');

-- 2) tenant_admin 授权 --------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.perm_code IN ('sys:assistant:define', 'sys:assistant:chat')
     AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id);

-- 3) 菜单 ---------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:assistant-definition'), 1, 15), 16, 10) AS UNSIGNED), 1, 0,
       '助手管理', '/assistant/definition', 'assistant/definition/index', 'robot', 60, 'MENU', 'sys:assistant:define', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/assistant/definition' AND deleted = 0);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:assistant-usage'), 1, 15), 16, 10) AS UNSIGNED), 1, 0,
       '助手用量', '/assistant/usage', 'assistant/usage/index', 'bar-chart', 61, 'MENU', 'sys:assistant:define', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/assistant/usage' AND deleted = 0);
