-- =====================================================================
-- Wave 5.94 M0：商城服务（whale-mall）权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.94-mall.sql
-- 幂等说明 : 权限、授权、菜单均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 边界说明 : 本脚本只登记「商城域」权限。支付（/pay/**）与文件（/file/**）权限分别在
--            各自迁移中登记，商城不重复定义，也不借用。
-- 权限编号 : 2200–2213（2100–2115 已被邮箱与地图占用，2200 起经全量扫描确认空闲）。
-- 菜单编号 : 491–499（sys_menu 独立号段，492–499 在 sys_permission 中被 ERP 占用、互不冲突）。
-- 授权口径 : tenant_admin 获得全部 14 项。
--            买家侧三项（2210 购物车与下单 / 2211 订单查看 / 2213 订单取消）应通过
--            「商城买家」角色授予全员，不要塞进 tenant_admin——那等于把下单能力
--            和改商品、发货能力绑在一起。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点（2200–2213）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2200, 0, 'sys:mall:browse', '商城浏览与门面信息', 'API', '/mall/meta', 'GET', 2200, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2200 OR perm_code = 'sys:mall:browse');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2201, 0, 'sys:mall:category:list', '查看商城类目', 'API', '/mall/category/tree', 'GET', 2201, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2201 OR perm_code = 'sys:mall:category:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2202, 0, 'sys:mall:category:manage', '维护商城类目', 'API', '/mall/category,/mall/category/{id}', 'POST', 2202, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2202 OR perm_code = 'sys:mall:category:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2203, 0, 'sys:mall:product:list', '查看商城商品', 'API', '/mall/product/page,/mall/product/{id}', 'GET', 2203, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2203 OR perm_code = 'sys:mall:product:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2204, 0, 'sys:mall:product:manage', '维护商城商品与价目', 'API', '/mall/product,/mall/product/{id}', 'POST', 2204, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2204 OR perm_code = 'sys:mall:product:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2205, 0, 'sys:mall:product:publish', '商品上下架', 'API', '/mall/product/{id}/status', 'PUT', 2205, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2205 OR perm_code = 'sys:mall:product:publish');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2206, 0, 'sys:mall:stock:list', '查看商城库存', 'API', '/mall/stock/page', 'GET', 2206, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2206 OR perm_code = 'sys:mall:stock:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2207, 0, 'sys:mall:stock:manage', '调整商城库存', 'API', '/mall/stock/{skuId}', 'PUT', 2207, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2207 OR perm_code = 'sys:mall:stock:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2208, 0, 'sys:mall:channel:list', '查看商城渠道', 'API', '/mall/channel/page', 'GET', 2208, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2208 OR perm_code = 'sys:mall:channel:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2209, 0, 'sys:mall:channel:manage', '维护商城渠道', 'API', '/mall/channel,/mall/channel/{id},/mall/channel/{id}/status', 'POST', 2209, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2209 OR perm_code = 'sys:mall:channel:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2210, 0, 'sys:mall:cart:manage', '购物车与下单（买家自助）', 'API', '/mall/cart,/mall/cart/item,/mall/cart/item/{id},/mall/cart/select,/mall/order,/mall/order/preview', 'POST', 2210, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2210 OR perm_code = 'sys:mall:cart:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2211, 0, 'sys:mall:order:list', '查看订单', 'API', '/mall/order/my,/mall/order/page,/mall/order/{id},/mall/order/{id}/complete', 'GET', 2211, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2211 OR perm_code = 'sys:mall:order:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2212, 0, 'sys:mall:order:manage', '订单发货', 'API', '/mall/order/{id}/ship', 'POST', 2212, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2212 OR perm_code = 'sys:mall:order:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2213, 0, 'sys:mall:order:cancel', '取消订单', 'API', '/mall/order/{id}/cancel', 'POST', 2213, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2213 OR perm_code = 'sys:mall:order:cancel');

-- ---------------------------------------------------------------------
-- 2. 租户管理员授权（role_code = tenant_admin，按租户逐一授权）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (2200, 2201, 2202, 2203, 2204, 2205, 2206, 2207, 2208, 2209, 2210, 2211, 2212, 2213) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 管理后台菜单（结构对齐 migration-5.83-crm-frontend：目录 + 子菜单 + 路由型详情页）
--    菜单号段 491–499（sys_menu 与 sys_permission 号段独立；492–499 已被 ERP 占用的是
--    sys_permission，sys_menu 侧经全量扫描确认空闲）。
-- ---------------------------------------------------------------------
-- 目录
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 491, 1, 0, '商城', '/mall', NULL, 'shop', 491, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 491);

-- 子菜单（visible = 1，进入侧边栏）
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 492, 1, 491, '商品管理', '/mall/product', 'mall/product/index', 'shopping', 1, 'MENU', 'sys:mall:product:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 492);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 493, 1, 491, '类目管理', '/mall/category', 'mall/category/index', 'apartment', 2, 'MENU', 'sys:mall:category:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 493);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 494, 1, 491, '库存管理', '/mall/stock', 'mall/stock/index', 'database', 3, 'MENU', 'sys:mall:stock:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 494);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 495, 1, 491, '渠道管理', '/mall/channel', 'mall/channel/index', 'branch', 4, 'MENU', 'sys:mall:channel:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 495);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 496, 1, 491, '订单管理', '/mall/order', 'mall/order/index', 'profile', 5, 'MENU', 'sys:mall:order:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 496);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 497, 1, 491, '购物车', '/mall/cart', 'mall/cart/index', 'cart', 6, 'MENU', 'sys:mall:cart:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 497);

-- 路由型详情页：visible = 0（不在侧边栏展示），仅登记权限归属
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 498, 1, 491, '商品详情', '/mall/product/:id', 'mall/product/detail', 'profile', 7, 'MENU', 'sys:mall:product:list', 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 498);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 499, 1, 491, '订单详情', '/mall/order/:id', 'mall/order/detail', 'profile', 8, 'MENU', 'sys:mall:order:list', 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 499);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 2200 AND 2213;
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 491 AND 499 ORDER BY id;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 2200 AND 2213 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 以 admin 登录后 GET /auth/me，确认 menus 树包含「商城」目录及 6 个子菜单
-- =====================================================================
