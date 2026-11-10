-- ---------------------------------------------------------------------
-- Wave 5.83 增量（Step 1a）：客户关系应用（whale-crm）前端菜单登记 + CRM 角色菜单可见性
--
-- 背景：
--   - 5.77 建立客户域权限码（337–354），5.83 冻结 M0/M1 权限码（1800–1899）与 CRM 角色；
--   - 5.83 仅预留「菜单编号 413–420」给 M1 客户页面，前端未交付前未登记菜单；
--   - 本次 Step 1a 交付 /crm 客户域页面，故启用 413–416：
--       413 客户关系（DIR，父菜单）
--       414 客户列表（/crm/customer）
--       415 公海客户（/crm/pool）
--       416 客户详情（/crm/customer/:id，隐藏菜单）
--   - 470–489 已被党政工会 5.88 占用，490 已被邮箱 5.89 占用，本脚本不触碰。
--
-- 幂等：全部 INSERT ... SELECT ... WHERE NOT EXISTS；不重复插入 1800–1899 权限。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 1. 菜单：413–416
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 413, 1, 0, '客户关系', '/crm', NULL, 'team', 413, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 413);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 414, 1, 413, '客户列表', '/crm/customer', 'crm/customer/index', 'user', 1, 'MENU', 'sys:crm:customer:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 414);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 415, 1, 413, '公海客户', '/crm/pool', 'crm/pool/index', 'swap', 2, 'MENU', 'sys:crm:pool:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 415);

-- 详情页为路由型菜单：visible = 0（不在侧边栏展示），仅登记权限归属
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 416, 1, 413, '客户详情', '/crm/customer/:id', 'crm/customer/detail', 'profile', 3, 'MENU', 'sys:crm:customer:list', 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 416);

-- ---------------------------------------------------------------------
-- 2. 菜单可见性授权
--    菜单可见性由 permission_code 决定；此处按权限码（而非写死 1800–1899 主键）
--    补齐授权，避免 5.83 中部分权限主键因 5.77 已占用同码而被跳过的问题。
--
--    sys:crm:customer:list → 全部 CRM 角色（客户列表 / 客户详情）
--    sys:crm:pool:view     → tenant_admin / crm_admin / crm_sales_manager /
--                            crm_sales / crm_analyst
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code = 'sys:crm:customer:list'
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('tenant_admin', 'crm_admin', 'crm_sales_manager', 'crm_sales',
                          'crm_service', 'crm_marketing', 'crm_analyst', 'crm_member')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code = 'sys:crm:pool:view'
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('tenant_admin', 'crm_admin', 'crm_sales_manager', 'crm_sales',
                          'crm_analyst')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
