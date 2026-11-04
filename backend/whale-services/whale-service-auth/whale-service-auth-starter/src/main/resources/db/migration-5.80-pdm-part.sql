-- =====================================================================
-- Wave 5.80 M1A：PDM 零件主档 + EBOM 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-part.sql
-- 幂等说明 : 权限、角色、角色复制、菜单与授权均为 NOT EXISTS 幂等执行，
--            可重复执行；不删除任何既有数据。
-- 背景     : whale-pdm 新增零件主档、物料分类、EBOM 结构与发布快照。
-- 权限编号 : PDM 段 900–1099；本脚本使用 930–939（940–949 预留）。
-- 菜单编号 : PDM 段 180–219；本脚本使用 185（零件库）、186（BOM 结构）。
-- 角色编号 : 平台角色 21104 pdm_engineer；业务租户按 5.65 口径复制同名角色。
-- 授权口径 : tenant_admin / pdm_manager / pdm_engineer 获得 930–939；
--            pdm_member 仅获得查询 930（零件）与 935（BOM）。
-- 前置条件 : bluewhale_pdm 已执行 V4__part_bom.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 930–939
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 930, 0, 'sys:pdm:part:list', '查询零件主档', 'API', '/pdm/part/page,/pdm/part/{id},/pdm/part/{id}/where-used,/pdm/part/category/tree,/pdm/part/category/{id}', 'GET', 930, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 930 OR perm_code = 'sys:pdm:part:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 931, 0, 'sys:pdm:part:add', '新增零件', 'API', '/pdm/part', 'POST', 931, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 931 OR perm_code = 'sys:pdm:part:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 932, 0, 'sys:pdm:part:edit', '编辑零件', 'API', '/pdm/part/{id}', 'PUT', 932, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 932 OR perm_code = 'sys:pdm:part:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 933, 0, 'sys:pdm:part:delete', '删除零件', 'API', '/pdm/part/{id}', 'DELETE', 933, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 933 OR perm_code = 'sys:pdm:part:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 934, 0, 'sys:pdm:part:category:manage', '维护物料分类', 'API', '/pdm/part/category,/pdm/part/category/{id}', 'POST/PUT/DELETE', 934, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 934 OR perm_code = 'sys:pdm:part:category:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 935, 0, 'sys:pdm:bom:list', '查询 BOM', 'API', '/pdm/bom/page,/pdm/bom/{id},/pdm/bom/{id}/releases', 'GET', 935, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 935 OR perm_code = 'sys:pdm:bom:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 936, 0, 'sys:pdm:bom:add', '新建 BOM', 'API', '/pdm/bom', 'POST', 936, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 936 OR perm_code = 'sys:pdm:bom:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 937, 0, 'sys:pdm:bom:edit', '编辑 BOM 结构', 'API', '/pdm/bom/{id}/lines,/pdm/bom/{id}/lines/{lineId}', 'POST/PUT/DELETE', 937, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 937 OR perm_code = 'sys:pdm:bom:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 938, 0, 'sys:pdm:bom:delete', '删除 BOM', 'API', '/pdm/bom/{id}', 'DELETE', 938, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 938 OR perm_code = 'sys:pdm:bom:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 939, 0, 'sys:pdm:bom:release', '发布 BOM', 'API', '/pdm/bom/{id}/release', 'POST', 939, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 939 OR perm_code = 'sys:pdm:bom:release');

-- ---------------------------------------------------------------------
-- 2. 角色模板（平台租户 tenant_id = 1）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21104, 1, 'pdm_engineer', 'PDM 工程师', '零件主档/物料分类/EBOM 结构/发布快照', 214, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21104 OR (tenant_id = 1 AND role_code = 'pdm_engineer'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':pdm_engineer')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'pdm_engineer' AS role_code, 'PDM 工程师' AS role_name, '零件主档/物料分类/EBOM 结构/发布快照' AS description, 214 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_engineer') AND p.id BETWEEN 930 AND 939) OR (r.role_code = 'pdm_member' AND p.id IN (930, 935)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 185, 1, 0, '零件库', '/pdm/part', 'pdm/part/index', 'appstore', 185, 'MENU', 'sys:pdm:part:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 185);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 186, 1, 0, 'BOM 结构', '/pdm/bom', 'pdm/bom/index', 'partition', 186, 'MENU', 'sys:pdm:bom:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 186);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 930 AND 939 ORDER BY id;
--   -- 期望：10 行，930..939 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_engineer','pdm_member')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager=10、pdm_engineer=10、pdm_member=2（另加既有产品/文档权限）。
--   -- 3) 每个业务租户都应有 pdm_engineer 角色
--   SELECT t.tenant_code, COUNT(r.id) AS role_count
--   FROM sys_tenant t
--   LEFT JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0 AND r.role_code = 'pdm_engineer'
--   WHERE t.deleted = 0 AND t.status = 1 AND (t.id = 1 OR t.tenant_type = 'TENANT')
--   GROUP BY t.tenant_code;
--   -- 期望：每行 role_count = 1。
-- =====================================================================
