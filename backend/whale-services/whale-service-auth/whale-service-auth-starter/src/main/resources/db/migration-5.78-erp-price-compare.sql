-- =====================================================================
-- Wave 5.78 M2 剩余 B 第五期：ERP 采购比价与价格趋势 权限增量（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-price-compare.sql
-- 幂等说明 : NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : whale-erp 价格表新增供应商比价与物料采购价格趋势查询。
-- 权限编号 : 已用至 538（第四期安全库存），本脚本使用 539–540；541–549 预留。
-- 菜单编号 : 不新增菜单，入口挂在既有菜单 102「价格表」页签内。
-- 授权口径 : 四个 ERP 角色均可查询（分析类只读）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限 539–540
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 539, 0, 'sys:erp:price-compare:compare', '供应商采购比价', 'API', '/erp/scm/price-list/compare', 'GET', 539, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 539 OR perm_code = 'sys:erp:price-compare:compare');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 540, 0, 'sys:erp:price-compare:trend', '物料采购价格趋势', 'API', '/erp/scm/price-list/trend', 'GET', 540, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 540 OR perm_code = 'sys:erp:price-compare:trend');

-- ---------------------------------------------------------------------
-- 2. 授权 tenant_admin / erp_manager / erp_scm / erp_member（只读）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 539 AND 540 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_scm', 'erp_member')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 校验：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 539 AND 540;
--   SELECT r.tenant_id, r.role_code, COUNT(*) FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 539 AND 540 GROUP BY r.tenant_id, r.role_code;
--   -- 期望每个 ERP 角色 2 项。
-- =====================================================================

