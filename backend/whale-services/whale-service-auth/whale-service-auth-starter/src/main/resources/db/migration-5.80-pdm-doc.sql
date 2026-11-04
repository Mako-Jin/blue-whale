-- =====================================================================
-- Wave 5.80 M0：PDM 图文档中心权限 / 角色 / 菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-doc.sql
-- 幂等说明 : 权限、角色、角色复制、菜单与授权均为 NOT EXISTS / 条件 UPDATE 幂等执行，
--            可重复执行；不删除任何既有数据。
-- 背景     : whale-pdm 新增图文档中心（目录树 / 文档主档 / 版本上传、检出、检入、
--            发布、回滚、下载）。文件字节仍存 whale-file，审批仍走 OA。
-- 权限编号 : PDM 段 900–1099；本脚本使用 900–909（目录维护复用 909）。
-- 菜单编号 : PDM 段 180–219；本脚本使用 180（图文档中心），
--            并把既有菜单 27（/pdm）改到 /pdm/product，避免落入图文档默认页。
-- 角色编号 : 平台角色 21103 pdm_doc_manager；业务租户按 5.65 口径复制同名角色。
-- 授权口径 : tenant_admin / pdm_manager / pdm_doc_manager 获得 900–909（10 项）；
--            pdm_member 仅获得查询 900 与下载 908（2 项）。
-- 前置条件 : bluewhale_pdm 已执行 V2__document_center.sql（pdm_doc_* 四表）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 900–909
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 900, 0, 'sys:pdm:doc:list', '查询图文档', 'API', '/pdm/document/page,/pdm/document/{id},/pdm/document/{id}/versions,/pdm/folder/tree,/pdm/folder/{id}', 'GET', 900, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 900 OR perm_code = 'sys:pdm:doc:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 901, 0, 'sys:pdm:doc:add', '新建图文档', 'API', '/pdm/document', 'POST', 901, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 901 OR perm_code = 'sys:pdm:doc:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 902, 0, 'sys:pdm:doc:edit', '编辑图文档', 'API', '/pdm/document/{id}', 'PUT', 902, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 902 OR perm_code = 'sys:pdm:doc:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 903, 0, 'sys:pdm:doc:delete', '删除图文档', 'API', '/pdm/document/{id}', 'DELETE', 903, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 903 OR perm_code = 'sys:pdm:doc:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 904, 0, 'sys:pdm:doc:version', '上传/检入版本', 'API', '/pdm/document/{id}/versions,/pdm/document/{id}/checkin', 'POST', 904, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 904 OR perm_code = 'sys:pdm:doc:version');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 905, 0, 'sys:pdm:doc:checkout', '检出图文档', 'API', '/pdm/document/{id}/checkout', 'POST', 905, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 905 OR perm_code = 'sys:pdm:doc:checkout');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 906, 0, 'sys:pdm:doc:release', '发布图文档版本', 'API', '/pdm/document/{id}/versions/{versionId}/release', 'POST', 906, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 906 OR perm_code = 'sys:pdm:doc:release');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 907, 0, 'sys:pdm:doc:rollback', '回滚图文档版本', 'API', '/pdm/document/{id}/versions/{versionId}/rollback', 'POST', 907, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 907 OR perm_code = 'sys:pdm:doc:rollback');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 908, 0, 'sys:pdm:doc:download', '下载图文档', 'API', '/pdm/document/{id}/versions/{versionId}/content', 'GET', 908, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 908 OR perm_code = 'sys:pdm:doc:download');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 909, 0, 'sys:pdm:folder:manage', '维护图文档目录', 'API', '/pdm/folder,/pdm/folder/{id}', 'POST/PUT/DELETE', 909, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 909 OR perm_code = 'sys:pdm:folder:manage');

-- ---------------------------------------------------------------------
-- 2. 角色模板（平台租户 tenant_id = 1）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21103, 1, 'pdm_doc_manager', 'PDM 图文档管理员', '图文档全量：目录维护/文档增删改/版本上传检入/检出/发布/回滚/下载', 213, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21103 OR (tenant_id = 1 AND role_code = 'pdm_doc_manager'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制角色（新租户由 TenantProvisioningAppService 按模板下发）
--    角色管理接口按 TenantContext 过滤 tenant_id，平台模板在业务租户不可见，
--    因此为每个既有业务租户复制一份同名角色；已存在同名角色则跳过。
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':pdm_doc_manager')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'pdm_doc_manager' AS role_code, 'PDM 图文档管理员' AS role_name, '图文档全量：目录维护/文档增删改/版本上传检入/检出/发布/回滚/下载' AS description, 213 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与既有业务租户）
--    授权 ID = MD5(role_id + ':' + permission_id) 前 15 位，稳定且幂等；
--    uk_role_permission(role_id, permission_id) 兜底重复插入。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_doc_manager') AND p.id BETWEEN 900 AND 909) OR (r.role_code = 'pdm_member' AND p.id IN (900, 908)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单
--    既有菜单 27（产品管理 /pdm）改到 /pdm/product：图文档中心改造后子应用根路径
--    默认进入 /pdm/document，产品页必须显式指向 /pdm/product，避免菜单错位。
-- ---------------------------------------------------------------------
UPDATE sys_menu SET path = '/pdm/product' WHERE id = 27 AND path = '/pdm';

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 180, 1, 0, '图文档中心', '/pdm/document', 'pdm/document/index', 'file-text', 180, 'MENU', 'sys:pdm:doc:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 180);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 900 AND 909 ORDER BY id;
--   -- 期望：10 行，900..909 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_doc_manager','pdm_member')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager=10、pdm_doc_manager=10、pdm_member=2（另加既有 211–214 产品权限）。
--   -- 3) 每个业务租户都应有 pdm_doc_manager 角色
--   SELECT t.tenant_code, COUNT(r.id) AS role_count
--   FROM sys_tenant t
--   LEFT JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0 AND r.role_code = 'pdm_doc_manager'
--   WHERE t.deleted = 0 AND t.status = 1 AND (t.id = 1 OR t.tenant_type = 'TENANT')
--   GROUP BY t.tenant_code;
--   -- 期望：每行 role_count = 1。
-- =====================================================================
