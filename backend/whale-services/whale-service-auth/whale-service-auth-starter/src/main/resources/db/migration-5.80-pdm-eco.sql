-- =====================================================================
-- Wave 5.80 M2-3：PDM 变更单（ECO）权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-eco.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除任何既有数据，不新增角色（复用 21105 pdm_change_manager）。
-- 背景     : whale-pdm 新增变更单（ECO）与变更任务、影响评估与执行计划。
-- 权限编号 : PDM 段 900–1099；本脚本使用 956–962（963–969 预留给 ECN/追溯/通知）。
-- 菜单编号 : PDM 段 180–219；本脚本使用 191（变更单），192–194 预留给 ECN/追溯/通知。
-- 授权口径 : tenant_admin / pdm_manager / pdm_change_manager 获得 956–962；
--            pdm_member 仅获得查询 956。
-- 前置条件 : bluewhale_pdm 已执行 V8__change_order.sql；
--            已执行 migration-5.80-pdm-change.sql（提供 190 菜单与 21105 角色）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 956–962
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 956, 0, 'sys:pdm:change:order:list', '查询变更单', 'API', '/pdm/change/order/page,/pdm/change/order/{id}', 'GET', 956, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 956 OR perm_code = 'sys:pdm:change:order:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 957, 0, 'sys:pdm:change:order:add', '新建变更单', 'API', '/pdm/change/order', 'POST', 957, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 957 OR perm_code = 'sys:pdm:change:order:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 958, 0, 'sys:pdm:change:order:edit', '编辑变更单/任务', 'API', '/pdm/change/order/{id},/pdm/change/order/{id}/assess,/pdm/change/order/{id}/cancel,/pdm/change/order/{id}/close,/pdm/change/order/{id}/tasks,/pdm/change/order/{id}/tasks/{taskId}', 'PUT/POST/DELETE', 958, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 958 OR perm_code = 'sys:pdm:change:order:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 959, 0, 'sys:pdm:change:order:delete', '删除变更单', 'API', '/pdm/change/order/{id}', 'DELETE', 959, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 959 OR perm_code = 'sys:pdm:change:order:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 960, 0, 'sys:pdm:change:order:submit', '提交变更单', 'API', '/pdm/change/order/{id}/submit', 'POST', 960, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 960 OR perm_code = 'sys:pdm:change:order:submit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 961, 0, 'sys:pdm:change:order:approve', '审批变更单', 'API', '/pdm/change/order/{id}/approve,/pdm/change/order/{id}/reject', 'POST', 961, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 961 OR perm_code = 'sys:pdm:change:order:approve');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 962, 0, 'sys:pdm:change:order:execute', '执行变更单', 'API', '/pdm/change/order/{id}/execute,/pdm/change/order/{id}/tasks/{taskId}/complete,/pdm/change/order/{id}/complete', 'POST', 962, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 962 OR perm_code = 'sys:pdm:change:order:execute');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_change_manager') AND p.id BETWEEN 956 AND 962) OR (r.role_code = 'pdm_member' AND p.id = 956) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 191, 1, 0, '变更单', '/pdm/change/order', 'pdm/change/order', 'swap', 191, 'MENU', 'sys:pdm:change:order:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 191);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 956 AND 962 ORDER BY id;
--   -- 期望：7 行，956..962 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_change_manager','pdm_member')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager/pdm_change_manager 各新增 7，pdm_member 新增 1。
--   -- 3) 菜单
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 191;
--   -- 期望：变更单 / /pdm/change/order / pdm/change/order / sys:pdm:change:order:list。
-- =====================================================================
