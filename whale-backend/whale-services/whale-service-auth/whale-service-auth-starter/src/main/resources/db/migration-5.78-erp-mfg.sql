-- =====================================================================
-- Wave 5.78 M3：ERP 生产计划权限/角色/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-mfg.sql
-- 幂等说明 : 权限、角色、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 模块化单体内的 MFG 上下文（需求预测、主生产计划、生产订单），
--            生产领料/完工入库联动库存，并生成生产领料/完工凭证；本脚本登记权限点、
--            角色与菜单。
-- 权限编号 : 已用至 440（调拨/盘点），本脚本使用 441–455。
-- 菜单编号 : 已用至 83（调拨/盘点），本脚本使用 84–86。
-- 角色编号 : 平台角色 25113 erp_mfg；业务租户复制角色与授权 ID 采用
--            「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : tenant_admin / erp_manager / erp_mfg 获得 441–455（15 项）；
--            erp_member / erp_finance 仅获得查询 441/445/449。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 需求预测权限 441–444
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 441, 0, 'sys:erp:forecast:list', '查询需求预测', 'API', '/erp/mfg/forecast/page,/erp/mfg/forecast/{id}', 'GET', 441, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 441 OR perm_code = 'sys:erp:forecast:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 442, 0, 'sys:erp:forecast:add', '新增需求预测', 'API', '/erp/mfg/forecast', 'POST', 442, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 442 OR perm_code = 'sys:erp:forecast:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 443, 0, 'sys:erp:forecast:publish', '发布需求预测', 'API', '/erp/mfg/forecast/{id}/publish', 'POST', 443, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 443 OR perm_code = 'sys:erp:forecast:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 444, 0, 'sys:erp:forecast:cancel', '取消需求预测', 'API', '/erp/mfg/forecast/{id}/cancel', 'POST', 444, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 444 OR perm_code = 'sys:erp:forecast:cancel');

-- ---------------------------------------------------------------------
-- 2. 主生产计划权限 445–448
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 445, 0, 'sys:erp:mps:list', '查询主生产计划', 'API', '/erp/mfg/mps/page,/erp/mfg/mps/{id}', 'GET', 445, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 445 OR perm_code = 'sys:erp:mps:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 446, 0, 'sys:erp:mps:add', '新增主生产计划', 'API', '/erp/mfg/mps', 'POST', 446, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 446 OR perm_code = 'sys:erp:mps:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 447, 0, 'sys:erp:mps:release', '下达主生产计划', 'API', '/erp/mfg/mps/{id}/release', 'POST', 447, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 447 OR perm_code = 'sys:erp:mps:release');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 448, 0, 'sys:erp:mps:cancel', '取消主生产计划', 'API', '/erp/mfg/mps/{id}/cancel', 'POST', 448, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 448 OR perm_code = 'sys:erp:mps:cancel');

-- ---------------------------------------------------------------------
-- 3. 生产订单权限 449–455
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 449, 0, 'sys:erp:production:list', '查询生产订单', 'API', '/erp/mfg/production/page,/erp/mfg/production/{id}', 'GET', 449, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 449 OR perm_code = 'sys:erp:production:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 450, 0, 'sys:erp:production:add', '新增生产订单', 'API', '/erp/mfg/production', 'POST', 450, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 450 OR perm_code = 'sys:erp:production:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 451, 0, 'sys:erp:production:release', '下达生产订单', 'API', '/erp/mfg/production/{id}/release', 'POST', 451, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 451 OR perm_code = 'sys:erp:production:release');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 452, 0, 'sys:erp:production:issue', '生产领料', 'API', '/erp/mfg/production/{id}/issue', 'POST', 452, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 452 OR perm_code = 'sys:erp:production:issue');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 453, 0, 'sys:erp:production:complete', '生产完工入库', 'API', '/erp/mfg/production/{id}/complete', 'POST', 453, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 453 OR perm_code = 'sys:erp:production:complete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 454, 0, 'sys:erp:production:close', '生产订单结案', 'API', '/erp/mfg/production/{id}/close', 'POST', 454, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 454 OR perm_code = 'sys:erp:production:close');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 455, 0, 'sys:erp:production:cancel', '取消生产订单', 'API', '/erp/mfg/production/{id}/cancel', 'POST', 455, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 455 OR perm_code = 'sys:erp:production:cancel');

-- ---------------------------------------------------------------------
-- 4. 平台租户业务角色模板 25113 erp_mfg
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25113, 1, 'erp_mfg', 'ERP 生产计划', '需求预测、主生产计划、生产订单与领料完工操作', 263, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25113 OR (tenant_id = 1 AND role_code = 'erp_mfg'));

-- ---------------------------------------------------------------------
-- 5. 既有业务租户复制 ERP 生产计划角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'erp_mfg' AS role_code, 'ERP 生产计划' AS role_name, '需求预测、主生产计划、生产订单与领料完工操作' AS description, 263 AS sort_no ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 6. 授权
--    tenant_admin / erp_manager / erp_mfg：441–455
--    erp_member / erp_finance：441/445/449（只读）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 441 AND 455 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_mfg')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (441, 445, 449) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member', 'erp_finance')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 7. 菜单 84–86（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 84, 1, 0, '需求预测', '/erp/mfg/forecast', 'erp/forecast', 'line-chart', 84, 'MENU', 'sys:erp:forecast:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 84);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 85, 1, 0, '主生产计划', '/erp/mfg/mps', 'erp/mps', 'calendar', 85, 'MENU', 'sys:erp:mps:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 85);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 86, 1, 0, '生产订单', '/erp/mfg/production', 'erp/production', 'tool', 86, 'MENU', 'sys:erp:production:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 86);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 441 AND 455;
--   SELECT id, role_code, role_name FROM sys_role WHERE role_code = 'erp_mfg';
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 84 AND 86;
-- =====================================================================
