-- =====================================================================
-- Wave 5.96 商城售后单与部分退款：售后权限
--   权限编号 : 2215 售后申请（全员）/ 2216 售后列表（运营）/ 2217 售后审核（运营）
--              2200–2213 已被 Wave 5.94 M0 使用，2214 为 Wave 5.95 整单退款。
--   授权口径 : tenant_admin 获得全部三个。买家侧 2215 与 CART_MANAGE 同口径（全员自助），
--              具体租户若要开放给非管理员角色，由租户在角色管理里自行勾选；
--              2216/2217 是运营动作，不对买家授予。
--   幂等口径 : 全部 NOT EXISTS，可重放。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, tenant_id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2215, 1, 0, 'sys:mall:aftersale:apply', '商城售后申请', 'API',
       '/mall/after-sale/**', 'POST', 2215, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2215)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:aftersale:apply');

INSERT INTO sys_permission (id, tenant_id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2216, 1, 0, 'sys:mall:aftersale:list', '商城售后列表', 'API',
       '/mall/after-sale/admin/page', 'GET', 2216, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2216)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:aftersale:list');

INSERT INTO sys_permission (id, tenant_id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2217, 1, 0, 'sys:mall:aftersale:audit', '商城售后审核', 'API',
       '/mall/after-sale/*/approve', 'POST', 2217, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2217)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:aftersale:audit');

-- ---------------------------------------------------------------------
-- 2. tenant_admin 授权（三个权限点各一条）
--    主键取 MD5(role:permission) 稳定哈希，与 5.94/5.95 口径一致；双重防重：
--    主键冲突或 (role, permission) 已存在均跳过。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
             r.id AS role_id, p.id AS permission_id
      FROM sys_role r
      JOIN sys_permission p ON p.id IN (2215, 2216, 2217) AND p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt
                  WHERE tgt.id = src.id
                     OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id IN (2215, 2216, 2217);
--   SELECT r.role_code, COUNT(*) FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id IN (2215, 2216, 2217) AND r.deleted = 0 GROUP BY r.role_code;
-- =====================================================================
