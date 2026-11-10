-- =====================================================================
-- Wave 5.80 M2-2：PDM 变更申请（ECR）权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-change.sql
-- 幂等说明 : 权限、角色、角色复制、菜单与授权均为 NOT EXISTS 幂等执行，
--            可重复执行；不删除任何既有数据。
-- 背景     : whale-pdm 新增变更申请（ECR）主档与状态机。
-- 权限编号 : PDM 段 900–1099；本脚本使用 950–955（956–969 预留 ECO/ECN/追溯/通知）。
-- 菜单编号 : PDM 段 180–219；本脚本使用 190（变更管理），191–194 预留给 ECO/ECN。
-- 角色编号 : 平台角色 21105 pdm_change_manager；业务租户按 5.65 口径复制同名角色。
-- 授权口径 : tenant_admin / pdm_manager / pdm_change_manager 获得 950–955；
--            pdm_member 仅获得查询 950。
-- 前置条件 : bluewhale_pdm 已执行 V7__change_request.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 950–955
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 950, 0, 'sys:pdm:change:list', '查询变更申请', 'API', '/pdm/change/request/page,/pdm/change/request/{id}', 'GET', 950, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 950 OR perm_code = 'sys:pdm:change:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 951, 0, 'sys:pdm:change:add', '新建变更申请', 'API', '/pdm/change/request', 'POST', 951, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 951 OR perm_code = 'sys:pdm:change:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 952, 0, 'sys:pdm:change:edit', '编辑/关闭变更申请', 'API', '/pdm/change/request/{id},/pdm/change/request/{id}/close', 'PUT/POST', 952, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 952 OR perm_code = 'sys:pdm:change:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 953, 0, 'sys:pdm:change:delete', '删除变更申请', 'API', '/pdm/change/request/{id}', 'DELETE', 953, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 953 OR perm_code = 'sys:pdm:change:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 954, 0, 'sys:pdm:change:submit', '提交变更申请', 'API', '/pdm/change/request/{id}/submit', 'POST', 954, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 954 OR perm_code = 'sys:pdm:change:submit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 955, 0, 'sys:pdm:change:approve', '审批变更申请', 'API', '/pdm/change/request/{id}/approve,/pdm/change/request/{id}/reject', 'POST', 955, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 955 OR perm_code = 'sys:pdm:change:approve');

-- ---------------------------------------------------------------------
-- 2. 角色模板（平台租户 tenant_id = 1）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21105, 1, 'pdm_change_manager', 'PDM 变更管理员', '变更申请/影响对象/审批流转', 215, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21105 OR (tenant_id = 1 AND role_code = 'pdm_change_manager'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':pdm_change_manager')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'pdm_change_manager' AS role_code, 'PDM 变更管理员' AS role_name, '变更申请/影响对象/审批流转' AS description, 215 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_change_manager') AND p.id BETWEEN 950 AND 955) OR (r.role_code = 'pdm_member' AND p.id = 950) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 190, 1, 0, '变更管理', '/pdm/change', 'pdm/change/index', 'swap', 190, 'MENU', 'sys:pdm:change:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 190);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 950 AND 955 ORDER BY id;
--   -- 期望：6 行，950..955 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_change_manager','pdm_member')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager=6、pdm_change_manager=6、pdm_member=1（另加既有产品/文档/零件权限）。
--   -- 3) 每个业务租户都应有 pdm_change_manager 角色
--   SELECT t.tenant_code, COUNT(r.id) AS role_count
--   FROM sys_tenant t
--   LEFT JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0 AND r.role_code = 'pdm_change_manager'
--   WHERE t.deleted = 0 AND t.status = 1 AND (t.id = 1 OR t.tenant_type = 'TENANT')
--   GROUP BY t.tenant_code;
--   -- 期望：每行 role_count = 1。
-- =====================================================================
