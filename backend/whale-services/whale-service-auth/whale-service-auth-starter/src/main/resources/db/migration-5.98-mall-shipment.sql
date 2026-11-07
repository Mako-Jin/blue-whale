-- =====================================================================
-- Wave 5.98 商城物流与履约：发货单权限与菜单
--   权限编号 : 2223 发货单列表（运营）/ 2224 发货单维护（运营）
--              2200–2213 归 Wave 5.94 M0，2214 归 5.95 整单退款，
--              2215–2217 归 5.96 售后单，2218–2222 归 5.97 计价。
--   授权口径 : tenant_admin 获得两个。发货是纯运营动作，不对买家授予：
--              买家看物流走订单侧 /mall/shipment/order/{orderNo}，用的是
--              已有的 2211 订单查看权限，不需要新权限点。
--   菜单     : 503「发货管理」，挂在商城目录 491 下（491–502 已占用）。
--   幂等口径 : 全部 NOT EXISTS，可重放。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, tenant_id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2223, 1, 0, 'sys:mall:shipment:list', '商城发货单列表', 'API',
       '/mall/shipment/page,/mall/shipment/*', 'GET', 2223, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2223)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:shipment:list');

INSERT INTO sys_permission (id, tenant_id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2224, 1, 0, 'sys:mall:shipment:manage', '商城发货单维护', 'API',
       '/mall/shipment,/mall/shipment/*/delivered,/mall/shipment/*/cancel', 'POST', 2224, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2224)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:shipment:manage');

-- ---------------------------------------------------------------------
-- 2. tenant_admin 授权（两个权限点各一条）
--    主键取 MD5(role:permission) 稳定哈希，与 5.94–5.97 口径一致；双重防重。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
             r.id AS role_id, p.id AS permission_id
      FROM sys_role r
      JOIN sys_permission p ON p.id IN (2223, 2224) AND p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt
                  WHERE tgt.id = src.id
                     OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单（503，挂在「商城」目录 491 下）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 503, 1, 491, '发货管理', '/mall/shipment', 'mall/shipment/index', 'profile', 10, 'MENU', 'sys:mall:shipment:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 503);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 2223 AND 2224;
--   SELECT r.role_code, COUNT(*) FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 2223 AND 2224 AND r.deleted = 0 GROUP BY r.role_code;
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 503;
-- =====================================================================
