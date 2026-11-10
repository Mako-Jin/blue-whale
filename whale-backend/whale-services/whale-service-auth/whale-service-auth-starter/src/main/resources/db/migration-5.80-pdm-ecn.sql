-- =====================================================================
-- Wave 5.80 M2-4..M2-8：PDM 变更通知（ECN）、追溯、通知日志权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-ecn.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除任何既有数据，不新增角色（复用 21105 pdm_change_manager）。
-- 背景     : whale-pdm 新增 ECN 变更通知、生效任务、变更追溯与投递日志。
-- 权限编号 : PDM 段 900–1099；本脚本使用 963–969。
-- 菜单编号 : PDM 段 180–219；本脚本使用 192（变更通知）、193（变更追溯）、194（通知日志）。
-- 授权口径 : tenant_admin / pdm_manager / pdm_change_manager 获得 963–969；
--            pdm_member 仅获得查询 963 与追溯 968。
-- 前置条件 : bluewhale_pdm 已执行 V9__change_notice.sql；
--            已执行 migration-5.80-pdm-eco.sql（提供 191 菜单与 21105 角色）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 963–969
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 963, 0, 'sys:pdm:change:notice:list', '查询变更通知', 'API', '/pdm/change/notice/page,/pdm/change/notice/{id}', 'GET', 963, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 963 OR perm_code = 'sys:pdm:change:notice:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 964, 0, 'sys:pdm:change:notice:add', '新建变更通知', 'API', '/pdm/change/notice', 'POST', 964, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 964 OR perm_code = 'sys:pdm:change:notice:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 965, 0, 'sys:pdm:change:notice:edit', '编辑/关闭/取消变更通知', 'API', '/pdm/change/notice/{id},/pdm/change/notice/{id}/close,/pdm/change/notice/{id}/cancel', 'PUT/POST/DELETE', 965, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 965 OR perm_code = 'sys:pdm:change:notice:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 966, 0, 'sys:pdm:change:notice:notify', '发送/重试变更通知', 'API', '/pdm/change/notice/{id}/notify,/pdm/change/notice/{id}/retry-notify', 'POST', 966, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 966 OR perm_code = 'sys:pdm:change:notice:notify');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 967, 0, 'sys:pdm:change:notice:effective', '变更通知生效', 'API', '/pdm/change/notice/{id}/effective', 'POST', 967, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 967 OR perm_code = 'sys:pdm:change:notice:effective');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 968, 0, 'sys:pdm:change:trace:list', '查询变更追溯', 'API', '/pdm/change/trace/page,/pdm/change/trace/timeline', 'GET', 968, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 968 OR perm_code = 'sys:pdm:change:trace:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 969, 0, 'sys:pdm:change:notice:log', '查询变更通知日志', 'API', '/pdm/change/notice/{id}/notify-logs', 'GET', 969, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 969 OR perm_code = 'sys:pdm:change:notice:log');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_change_manager') AND p.id BETWEEN 963 AND 969) OR (r.role_code = 'pdm_member' AND p.id IN (963, 968)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 192, 1, 0, '变更通知', '/pdm/change/notice', 'pdm/change/notice', 'bell', 192, 'MENU', 'sys:pdm:change:notice:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 192);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 193, 1, 0, '变更追溯', '/pdm/change/trace', 'pdm/change/trace', 'timeline', 193, 'MENU', 'sys:pdm:change:trace:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 193);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 194, 1, 0, '通知日志', '/pdm/change/notice', 'pdm/change/notice', 'file-text', 194, 'MENU', 'sys:pdm:change:notice:log', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 194);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 963 AND 969 ORDER BY id;
--   -- 期望：7 行，963..969 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_change_manager','pdm_member')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager/pdm_change_manager 各新增 7，pdm_member 新增 2。
--   -- 3) 菜单
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 192 AND 194;
-- =====================================================================
