-- =====================================================================
-- Wave 5.80 M3：PDM 工艺管理权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-process.sql
-- 幂等说明 : 权限、角色、角色复制、菜单与授权均为 NOT EXISTS 幂等执行，
--            可重复执行；不删除任何既有数据。
-- 背景     : whale-pdm 新增工艺路线、工序、工装、SOP 与 MES 下发。
-- 权限编号 : PDM 段 900–1099；本脚本使用 970–976（977–979 预留）。
-- 菜单编号 : PDM 段 180–219；本脚本使用 195–197。
-- 角色编号 : 平台角色 21106 pdm_process_engineer；业务租户按 5.65 口径复制同名角色。
-- 授权口径 : tenant_admin / pdm_manager / pdm_engineer / pdm_process_engineer 获得 970–976；
--            pdm_member 仅获得查询 970。
-- 前置条件 : bluewhale_pdm 已执行 V10__process_route.sql；whale-mes 已执行 V9__mes_process_route.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 970–976
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 970, 0, 'sys:pdm:process:list', '查询工艺路线', 'API', '/pdm/process/route/page,/pdm/process/route/{id}', 'GET', 970, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 970 OR perm_code = 'sys:pdm:process:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 971, 0, 'sys:pdm:process:add', '新建工艺路线', 'API', '/pdm/process/route', 'POST', 971, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 971 OR perm_code = 'sys:pdm:process:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 972, 0, 'sys:pdm:process:edit', '编辑工艺路线', 'API', '/pdm/process/route/{id},/pdm/process/route/{id}/steps,/pdm/process/route/{id}/steps/{stepId},/pdm/process/route/{id}/toolings,/pdm/process/route/{id}/toolings/{toolingId},/pdm/process/route/{id}/sop,/pdm/process/route/{id}/sop/{sopId}', 'PUT/POST/DELETE', 972, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 972 OR perm_code = 'sys:pdm:process:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 973, 0, 'sys:pdm:process:delete', '删除工艺路线', 'API', '/pdm/process/route/{id}', 'DELETE', 973, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 973 OR perm_code = 'sys:pdm:process:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 974, 0, 'sys:pdm:process:release', '发布工艺路线', 'API', '/pdm/process/route/{id}/release', 'POST', 974, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 974 OR perm_code = 'sys:pdm:process:release');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 975, 0, 'sys:pdm:process:publish', '重试 MES 下发', 'API', '/pdm/process/route/{id}/publish/retry', 'POST', 975, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 975 OR perm_code = 'sys:pdm:process:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 976, 0, 'sys:pdm:process:withdraw', '撤回工艺路线', 'API', '/pdm/process/route/{id}/withdraw', 'POST', 976, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 976 OR perm_code = 'sys:pdm:process:withdraw');

-- ---------------------------------------------------------------------
-- 2. 角色模板（平台租户 tenant_id = 1）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21106, 1, 'pdm_process_engineer', 'PDM 工艺工程师', '工艺路线/工序/工装/SOP/MES 下发', 216, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21106 OR (tenant_id = 1 AND role_code = 'pdm_process_engineer'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':pdm_process_engineer')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'pdm_process_engineer' AS role_code, 'PDM 工艺工程师' AS role_name, '工艺路线/工序/工装/SOP/MES 下发' AS description, 216 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_engineer', 'pdm_process_engineer') AND p.id BETWEEN 970 AND 976) OR (r.role_code = 'pdm_member' AND p.id = 970) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 195, 1, 0, '工艺路线', '/pdm/process/route', 'pdm/process/route', 'apartment', 195, 'MENU', 'sys:pdm:process:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 195);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 196, 1, 0, '工序与工装', '/pdm/process/route', 'pdm/process/route', 'tool', 196, 'MENU', 'sys:pdm:process:edit', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 196);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 197, 1, 0, 'MES 下发', '/pdm/process/route', 'pdm/process/route', 'send', 197, 'MENU', 'sys:pdm:process:publish', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 197);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 970 AND 976 ORDER BY id;
--   -- 期望：7 行，970..976 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_engineer','pdm_process_engineer','pdm_member')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager=7、pdm_engineer=7、pdm_process_engineer=7、pdm_member=1（另加既有权限）。
--   -- 3) 每个业务租户都应有 pdm_process_engineer 角色
--   SELECT t.tenant_code, COUNT(r.id) AS role_count
--   FROM sys_tenant t
--   LEFT JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0 AND r.role_code = 'pdm_process_engineer'
--   WHERE t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
--   GROUP BY t.tenant_code;
-- =====================================================================
