-- =====================================================================
-- Wave 5.80 PDM 图文档导出权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-doc-export.sql
-- 幂等说明 : 权限登记与角色授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除任何既有数据。
-- 背景     : whale-pdm 图文档中心新增列表导出（CSV），补齐 M0-B 预留号段 912。
-- 权限编号 : PDM 段 900–1099；本脚本使用 912。
-- 授权口径 : tenant_admin / pdm_manager / pdm_doc_manager 获得 912；
--            pdm_member 不授予（只读查询与下载）。
-- 菜单     : 不新增（导出为文档列表页按钮）。
-- 前置条件 : bluewhale_pdm 已执行 V2__document_center.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 912
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 912, 0, 'sys:pdm:doc:export', '导出图文档', 'API', '/pdm/document/export', 'GET', 912, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 912 OR perm_code = 'sys:pdm:doc:export');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
--    授权 ID = MD5(role_id + ':' + permission_id) 前 15 位，稳定且幂等；
--    uk_role_permission(role_id, permission_id) 兜底重复插入。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_doc_manager') AND p.id = 912) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id = 912;
--   -- 期望：1 行，sys:pdm:doc:export / /pdm/document/export。
-- =====================================================================
