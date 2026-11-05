-- =====================================================================
-- Wave 5.80 M4：PDM 协同（交付物/评审/在线预览）权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-collab.sql
-- 幂等说明 : 权限、角色、角色复制、菜单与授权均为 NOT EXISTS 幂等执行，
--            可重复执行；不删除任何既有数据。
-- 背景     : whale-pdm 新增项目交付物归档、研发评审与在线预览任务。
-- 权限编号 : PDM 段 900–1099；本脚本使用 980–989（990–999 预留给 M5 集成对账）。
-- 菜单编号 : PDM 段 180–219；本脚本使用 198–200（201 预留）。
-- 角色编号 : 平台角色 21107 pdm_collab_manager；业务租户按 5.65 口径复制同名角色。
-- 授权口径 : tenant_admin / pdm_manager / pdm_collab_manager 获得 980–989；
--            pdm_engineer 获得 980/983/987（查询与预览）；pdm_member 仅获得 987。
-- 前置条件 : bluewhale_pdm 已执行 V11__collab_preview.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 980–989
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 980, 0, 'sys:pdm:collab:deliverable:list', '查询项目交付物', 'API', '/pdm/collab/deliverable/page', 'GET', 980, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 980 OR perm_code = 'sys:pdm:collab:deliverable:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 981, 0, 'sys:pdm:collab:deliverable:add', '归档项目交付物', 'API', '/pdm/collab/deliverable', 'POST', 981, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 981 OR perm_code = 'sys:pdm:collab:deliverable:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 982, 0, 'sys:pdm:collab:deliverable:delete', '取消交付物归档', 'API', '/pdm/collab/deliverable/{id}', 'DELETE', 982, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 982 OR perm_code = 'sys:pdm:collab:deliverable:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 983, 0, 'sys:pdm:collab:review:list', '查询研发评审', 'API', '/pdm/collab/review/page,/pdm/collab/review/{id}', 'GET', 983, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 983 OR perm_code = 'sys:pdm:collab:review:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 984, 0, 'sys:pdm:collab:review:add', '新建研发评审', 'API', '/pdm/collab/review', 'POST', 984, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 984 OR perm_code = 'sys:pdm:collab:review:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 985, 0, 'sys:pdm:collab:review:edit', '编辑/删除研发评审', 'API', '/pdm/collab/review/{id}', 'PUT/DELETE', 985, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 985 OR perm_code = 'sys:pdm:collab:review:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 986, 0, 'sys:pdm:collab:review:close', '关闭研发评审', 'API', '/pdm/collab/review/{id}/close', 'POST', 986, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 986 OR perm_code = 'sys:pdm:collab:review:close');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 987, 0, 'sys:pdm:collab:preview:list', '查询/读取在线预览', 'API', '/pdm/collab/preview/page,/pdm/collab/preview/{jobId}/content', 'GET', 987, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 987 OR perm_code = 'sys:pdm:collab:preview:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 988, 0, 'sys:pdm:collab:preview:create', '创建在线预览', 'API', '/pdm/collab/preview/{documentId}/versions/{versionId}', 'POST', 988, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 988 OR perm_code = 'sys:pdm:collab:preview:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 989, 0, 'sys:pdm:collab:preview:retry', '重试在线预览', 'API', '/pdm/collab/preview/{jobId}/retry', 'POST', 989, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 989 OR perm_code = 'sys:pdm:collab:preview:retry');

-- ---------------------------------------------------------------------
-- 2. 角色模板（平台租户 tenant_id = 1）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21107, 1, 'pdm_collab_manager', 'PDM 协同管理员', '项目交付物归档/研发评审/在线预览', 217, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21107 OR (tenant_id = 1 AND role_code = 'pdm_collab_manager'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':pdm_collab_manager')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'pdm_collab_manager' AS role_code, 'PDM 协同管理员' AS role_name, '项目交付物归档/研发评审/在线预览' AS description, 217 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_collab_manager') AND p.id BETWEEN 980 AND 989) OR (r.role_code = 'pdm_engineer' AND p.id IN (980, 983, 987)) OR (r.role_code = 'pdm_member' AND p.id = 987) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 198, 1, 0, '项目交付物', '/pdm/collab/deliverable', 'pdm/collab/deliverable/index', 'folder-open', 198, 'MENU', 'sys:pdm:collab:deliverable:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 198);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 199, 1, 0, '研发评审', '/pdm/collab/review', 'pdm/collab/review/index', 'audit', 199, 'MENU', 'sys:pdm:collab:review:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 199);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 200, 1, 0, '在线预览', '/pdm/collab/preview', 'pdm/collab/preview/index', 'eye', 200, 'MENU', 'sys:pdm:collab:preview:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 200);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 980 AND 989 ORDER BY id;
--   -- 期望：10 行，980..989 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_collab_manager','pdm_engineer','pdm_member')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager=10、pdm_collab_manager=10、pdm_engineer=3、pdm_member=1（另加既有权限）。
--   -- 3) 每个业务租户都应有 pdm_collab_manager 角色
--   SELECT t.tenant_code, COUNT(r.id) AS role_count
--   FROM sys_tenant t
--   LEFT JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0 AND r.role_code = 'pdm_collab_manager'
--   WHERE t.deleted = 0 AND t.status = 1 AND (t.id = 1 OR t.tenant_type = 'TENANT')
--   GROUP BY t.tenant_code;
--   -- 期望：每行 role_count = 1。
-- =====================================================================
