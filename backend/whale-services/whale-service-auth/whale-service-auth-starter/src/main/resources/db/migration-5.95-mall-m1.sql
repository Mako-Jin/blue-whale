-- =====================================================================
-- Wave 5.95 商城 M1 首切片：整单退款权限
--   权限编号 : 2214（2200–2213 已被 Wave 5.94 M0 使用）。
--   授权口径 : tenant_admin 获得整单退款（运营动作）；买家侧不授。
--   幂等口径 : 全部 NOT EXISTS，可重放。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点：整单退款
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, tenant_id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2214, 1, 0, 'sys:mall:order:refund', '商城订单整单退款', 'API',
       '/mall/order/*/refund', 'POST', 2214, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2214)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:order:refund');

-- ---------------------------------------------------------------------
-- 2. tenant_admin 授权
--    主键取 MD5(role:permission) 稳定哈希，与 5.94 口径一致；双重防重：
--    主键冲突或 (role, permission) 已存在均跳过。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
             r.id AS role_id, p.id AS permission_id
      FROM sys_role r
      JOIN sys_permission p ON p.id = 2214 AND p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt
                  WHERE tgt.id = src.id
                     OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id = 2214;
--   SELECT r.role_code, COUNT(*) FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id = 2214 AND r.deleted = 0 GROUP BY r.role_code;
-- =====================================================================
