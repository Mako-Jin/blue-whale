-- =====================================================================
-- Wave 5.80 M1B：PDM BOM 导出权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-bom-export.sql
-- 幂等说明 : 权限与授权均 NOT EXISTS 幂等执行，可重复执行；不删除任何数据。
-- 背景     : BOM 多视图派生、版本对比与 Excel 导出。
-- 权限编号 : PDM 段 930–949；本脚本新增 940（BOM 导出）。
-- 授权口径 : tenant_admin / pdm_manager / pdm_engineer 获得 940；
--            pdm_member 仅只读查询，不含导出。
-- 前置条件 : 已执行 migration-5.80-pdm-part.sql 与 bluewhale_pdm V5__bom_views.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 940：BOM 导出
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 940, 0, 'sys:pdm:bom:export', '导出 BOM', 'API', '/pdm/bom/{id}/export', 'GET', 940, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 940 OR perm_code = 'sys:pdm:bom:export');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_engineer') AND p.id = 940) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id = 940;
--   -- 期望：1 行 sys:pdm:bom:export。
-- =====================================================================
