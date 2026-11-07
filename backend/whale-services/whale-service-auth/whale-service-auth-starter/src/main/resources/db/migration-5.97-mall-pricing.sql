-- =====================================================================
-- Wave 5.97 商城计价重构：优惠券与运费权限
--   权限编号 : 2218 券模板列表（运营）/ 2219 券模板维护（运营）
--              2220 领券与我的券（全员）
--              2221 运费模板列表（运营）/ 2222 运费模板维护（运营）
--              2200–2213 归 Wave 5.94 M0，2214 归 5.95 整单退款，
--              2215–2217 归 5.96 售后单。
--   授权口径 : tenant_admin 获得全部五个。2220 是买家自助动作（领券、看自己的券），
--              与 CART_MANAGE / AFTER_SALE_APPLY 同口径；其余四个是运营动作，
--              不对买家授予——能领券不等于能改券面额。
--   幂等口径 : 全部 NOT EXISTS，可重放。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2218, 0, 'sys:mall:coupon:list', '商城优惠券列表', 'API',
       '/mall/coupon/page', 'GET', 2218, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2218)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:coupon:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2219, 0, 'sys:mall:coupon:manage', '商城优惠券维护', 'API',
       '/mall/coupon', 'POST', 2219, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2219)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:coupon:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2220, 0, 'sys:mall:coupon:claim', '商城领券与我的券', 'API',
       '/mall/coupon/mine', 'GET', 2220, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2220)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:coupon:claim');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2221, 0, 'sys:mall:freight:list', '商城运费模板列表', 'API',
       '/mall/freight-template/page', 'GET', 2221, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2221)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:freight:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2222, 0, 'sys:mall:freight:manage', '商城运费模板维护', 'API',
       '/mall/freight-template', 'POST', 2222, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2222)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:freight:manage');

-- ---------------------------------------------------------------------
-- 2. tenant_admin 授权（五个权限点各一条）
--    主键取 MD5(role:permission) 稳定哈希，与 5.94/5.95/5.96 口径一致；双重防重：
--    主键冲突或 (role, permission) 已存在均跳过。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
             r.id AS role_id, p.id AS permission_id
      FROM sys_role r
      JOIN sys_permission p ON p.id IN (2218, 2219, 2220, 2221, 2222) AND p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt
                  WHERE tgt.id = src.id
                     OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单（500–502，挂在「商城」目录 491 下）
--    号段说明：sys_menu 侧 491–499 已被 5.94 占用，500 起空闲
--    （500/501 在 sys_permission 侧属 ERP 报表，两个号段互不影响）。
--    502 售后单是补登：5.96 只落了权限 2215–2217、漏了菜单，
--    子应用顶栏能进但平台侧边栏看不到，这里一并补上。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 500, 1, 491, '售后单', '/mall/aftersale', 'mall/aftersale/index', 'profile', 7, 'MENU', 'sys:mall:aftersale:apply', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 500);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 501, 1, 491, '优惠券', '/mall/coupon', 'mall/coupon/index', 'shopping', 8, 'MENU', 'sys:mall:coupon:claim', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 501);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 502, 1, 491, '运费模板', '/mall/freight', 'mall/freight/index', 'branch', 9, 'MENU', 'sys:mall:freight:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 502);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 2218 AND 2222;
--   SELECT r.role_code, COUNT(*) FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 2218 AND 2222 AND r.deleted = 0 GROUP BY r.role_code;
--   SELECT id, name, path, component, permission_code FROM sys_menu
--   WHERE id BETWEEN 500 AND 502 ORDER BY id;
-- =====================================================================
