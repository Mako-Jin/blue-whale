-- =====================================================================
-- Wave 5.77 M0：客户关系应用（whale-crm）权限与角色登记增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.77-crm.sql
-- 幂等说明 : 权限、角色与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除既有数据。
-- 背景     : 需求门禁（ADR-003 / D2）未通过前只登记权限点与角色模板，
--            不写入任何客户 / 联系人 / 商机种子数据，也不注册菜单（避免出现
--            点击即 403 的空入口）；门禁通过后进入 M4 再追加菜单 57–62。
-- 权限编号 : 已用至 330（场景），本脚本使用 337–354（18 项），331–336 预留。
-- 菜单编号 : 预留 57–62（M4 启用时追加）。
-- 角色编号 : 平台角色 25108 crm_admin / 25109 crm_member；
--            业务租户复制角色与授权 ID 采用「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : crm_admin / tenant_admin 获得 337–354（18 项）；
--            crm_member 获得 338/343/345/350/351（查询与跟进）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（337–354）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 337, 0, 'sys:crm:browse', '查询 CRM 门禁状态', 'API', '/crm/meta', 'GET', 337, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 337 OR perm_code = 'sys:crm:browse');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 338, 0, 'sys:crm:customer:list', '查询客户', 'API', '/crm/customer/page,/crm/customer/{id}', 'GET', 338, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 338 OR perm_code = 'sys:crm:customer:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 339, 0, 'sys:crm:customer:add', '新建客户', 'API', '/crm/customer', 'POST', 339, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 339 OR perm_code = 'sys:crm:customer:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 340, 0, 'sys:crm:customer:edit', '编辑客户', 'API', '/crm/customer/{id},/crm/customer/{id}/status', 'PUT', 340, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 340 OR perm_code = 'sys:crm:customer:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 341, 0, 'sys:crm:customer:delete', '停用/删除客户', 'API', '/crm/customer/{id}/status,/crm/customer/{id}', 'DELETE,PUT', 341, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 341 OR perm_code = 'sys:crm:customer:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 342, 0, 'sys:crm:customer:merge', '合并客户', 'API', '/crm/customer/{id}/merge', 'POST', 342, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 342 OR perm_code = 'sys:crm:customer:merge');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 343, 0, 'sys:crm:contact:list', '查询联系人', 'API', '/crm/customer/{id}/contact', 'GET', 343, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 343 OR perm_code = 'sys:crm:contact:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 344, 0, 'sys:crm:contact:manage', '管理联系人', 'API', '/crm/customer/{id}/contact,/crm/contact/{id}', 'POST,PUT,DELETE', 344, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 344 OR perm_code = 'sys:crm:contact:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 345, 0, 'sys:crm:opportunity:list', '查询商机与漏斗', 'API', '/crm/opportunity/page,/crm/opportunity/funnel', 'GET', 345, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 345 OR perm_code = 'sys:crm:opportunity:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 346, 0, 'sys:crm:opportunity:add', '新建商机', 'API', '/crm/opportunity', 'POST', 346, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 346 OR perm_code = 'sys:crm:opportunity:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 347, 0, 'sys:crm:opportunity:edit', '编辑商机', 'API', '/crm/opportunity/{id}', 'PUT', 347, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 347 OR perm_code = 'sys:crm:opportunity:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 348, 0, 'sys:crm:opportunity:stage', '推进商机阶段', 'API', '/crm/opportunity/{id}/stage', 'PUT', 348, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 348 OR perm_code = 'sys:crm:opportunity:stage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 349, 0, 'sys:crm:opportunity:delete', '删除商机', 'API', '/crm/opportunity/{id}', 'DELETE', 349, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 349 OR perm_code = 'sys:crm:opportunity:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 350, 0, 'sys:crm:followup:add', '新增跟进记录', 'API', '/crm/follow-up', 'POST', 350, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 350 OR perm_code = 'sys:crm:followup:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 351, 0, 'sys:crm:followup:list', '查询跟进记录', 'API', '/crm/follow-up/page', 'GET', 351, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 351 OR perm_code = 'sys:crm:followup:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 352, 0, 'sys:crm:export', '导出客户数据', 'API', '/crm/customer/export', 'GET', 352, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 352 OR perm_code = 'sys:crm:export');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 353, 0, 'sys:crm:stats', '查看客户统计', 'API', '/crm/stats', 'GET', 353, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 353 OR perm_code = 'sys:crm:stats');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 354, 0, 'sys:crm:customer:assign', '分配客户负责人', 'API', '/crm/customer/{id}/owner', 'PUT', 354, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 354 OR perm_code = 'sys:crm:customer:assign');

-- ---------------------------------------------------------------------
-- 2. 平台租户业务角色模板（25108 crm_admin / 25109 crm_member）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25108, 1, 'crm_admin', 'CRM 管理员', '客户/联系人/商机/跟进/统计/导出全量管理入口（需求门禁通过后启用）', 258, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25108 OR (tenant_id = 1 AND role_code = 'crm_admin'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25109, 1, 'crm_member', 'CRM 成员', '客户与商机查询、跟进记录新增与查询（需求门禁通过后启用）', 259, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25109 OR (tenant_id = 1 AND role_code = 'crm_member'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制 CRM 角色（仅 tenant_type='TENANT' 的启用租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'crm_admin' AS role_code, 'CRM 管理员' AS role_name, '客户/联系人/商机/跟进/统计/导出全量管理入口（需求门禁通过后启用）' AS description, 258 AS sort_no UNION ALL SELECT 'crm_member', 'CRM 成员', '客户与商机查询、跟进记录新增与查询（需求门禁通过后启用）', 259 ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与全部业务租户）
--    crm_admin / tenant_admin：337–354（18 项）
--    crm_member：338/343/345/350/351（查询与跟进）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 337 AND 354 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('crm_admin', 'tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (338, 343, 345, 350, 351) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'crm_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 337 AND 354;
--   SELECT tenant_id, role_code FROM sys_role WHERE role_code IN ('crm_admin','crm_member') ORDER BY tenant_id;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 337 AND 354 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：crm_admin / tenant_admin 各 18 项；crm_member 各 5 项
--   -- 菜单 57–62 在 M4 启用时追加，门禁前不注册，避免空入口。
-- =====================================================================
