-- ---------------------------------------------------------------------
-- Wave 5.83 增量（Step 1b）：客户关系应用（whale-crm）销售域前端菜单登记
--
-- 背景：
--   - 5.84 建立线索/商机权限 1840–1849，5.85 建立报价权限 1850–1854，
--     5.86 建立预测/目标权限 1855–1859；三者均「菜单不登记（前端未交付）」；
--   - 5.83 预留菜单编号 413–420，其中 413–416 已在 Step 1a 登记客户域；
--   - 本次 Step 1b 交付 /crm 销售域页面，启用 417–420：
--       417 线索管理（/crm/lead）
--       418 商机管理（/crm/opportunity）
--       419 销售报价（/crm/quote）
--       420 销售预测（/crm/forecast，页内「销售目标」Tab 由 target:list 控权）
--   - 470–489 已被 5.88 占用、490 已被 5.89 占用，本脚本不触碰。
--
-- 幂等：全部 INSERT ... SELECT ... WHERE NOT EXISTS；不新增权限点，
--       角色授权沿用 5.84–5.86 既有结果。
-- ---------------------------------------------------------------------

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 417, 1, 413, '线索管理', '/crm/lead', 'crm/lead/index', 'bulb', 4, 'MENU', 'sys:crm:lead:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 417);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 418, 1, 413, '商机管理', '/crm/opportunity', 'crm/opportunity/index', 'aim', 5, 'MENU', 'sys:crm:opportunity:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 418);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 419, 1, 413, '销售报价', '/crm/quote', 'crm/quote/index', 'dollar', 6, 'MENU', 'sys:crm:quote:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 419);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 420, 1, 413, '销售预测', '/crm/forecast', 'crm/forecast/index', 'line-chart', 7, 'MENU', 'sys:crm:forecast:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 420);
