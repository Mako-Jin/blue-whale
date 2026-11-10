-- =====================================================================
-- Wave 5.78 M3 剩余：ERP 生产增强权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-production-enhance.sql
-- 幂等说明 : 权限与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp MFG 上下文新增工序报工、生产退料与生产报废；
--            本脚本登记权限点并授权；不新增菜单。
-- 权限编号 : 已用至 550（CRP），本脚本使用 551–553；554–569 预留。
-- 角色     : 沿用 25113 erp_mfg，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_mfg 获得 551–553；
--            erp_member 不授予（生产执行类操作）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 生产增强权限 551–553
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 551, 0, 'sys:erp:production:operation:report', '生产工序报工', 'API', '/erp/mfg/production/{id}/operations/{stepNo}/report', 'POST', 551, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 551 OR perm_code = 'sys:erp:production:operation:report');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 552, 0, 'sys:erp:production:return', '生产退料', 'API', '/erp/mfg/production/{id}/material/return', 'POST', 552, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 552 OR perm_code = 'sys:erp:production:return');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 553, 0, 'sys:erp:production:scrap', '生产报废', 'API', '/erp/mfg/production/{id}/material/scrap', 'POST', 553, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 553 OR perm_code = 'sys:erp:production:scrap');

-- ---------------------------------------------------------------------
-- 2. 授权：tenant_admin / erp_manager / erp_mfg：551–553
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 551 AND 553 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_mfg')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 551 AND 553;
-- =====================================================================
