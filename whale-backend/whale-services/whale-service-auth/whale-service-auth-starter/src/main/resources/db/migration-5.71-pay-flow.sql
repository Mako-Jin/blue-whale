-- =====================================================================
-- whale-pay 支付闭环：退款 / 对账权限（bluewhale_auth）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.71-pay-flow.sql
-- 幂等说明 : 权限与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 权限编号 : 234 已被 message M0 占用、239–243 已被 message M1 占用，本脚本使用 244–245。
-- 授权口径 : 存量 tenant_admin 此前未获得 sys:pay:*（70–73），本脚本一并补齐，
--            super_admin 由系统内置全量权限覆盖。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 244, 0, 'sys:pay:refund', '支付单退款', 'API', '/pay/order/{id}/refund', 'POST', 244, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 244 OR perm_code = 'sys:pay:refund');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 245, 0, 'sys:pay:reconcile', '支付单对账', 'API', '/pay/order/{id}/reconcile', 'GET', 245, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 245 OR perm_code = 'sys:pay:reconcile');

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (70, 71, 72, 73, 244, 245) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id IN (70, 71, 72, 73, 244, 245);
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id IN (70, 71, 72, 73, 244, 245) AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
-- =====================================================================
