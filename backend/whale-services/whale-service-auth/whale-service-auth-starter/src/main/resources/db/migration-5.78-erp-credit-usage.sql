-- =====================================================================
-- Wave 5.78 M2 剩余 B 第七期：ERP 客户信用占用查询 权限增量（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-credit-usage.sql
-- 幂等说明 : NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : 销售订单新增客户信用占用查询（未结应收 + 在途预占、可用额度）。
-- 权限编号 : 已用至 540（第五期采购比价），本脚本使用 541；542–549 预留。
-- 菜单编号 : 不新增菜单，入口在销售订单页新增客户时展示。
-- 授权口径 : 四个 ERP 角色均可查询（只读）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限 541
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 541, 0, 'sys:erp:credit-usage:view', '客户信用占用查询', 'API', '/erp/scm/sales-order/credit-usage', 'GET', 541, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 541 OR perm_code = 'sys:erp:credit-usage:view');

-- ---------------------------------------------------------------------
-- 2. 授权 tenant_admin / erp_manager / erp_scm / erp_member（只读）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id = 541 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_scm', 'erp_member')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 校验：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id = 541;
--   SELECT r.tenant_id, r.role_code, COUNT(*) FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id = 541 GROUP BY r.tenant_id, r.role_code;
--   -- 期望每个 ERP 角色 1 项。
-- =====================================================================
