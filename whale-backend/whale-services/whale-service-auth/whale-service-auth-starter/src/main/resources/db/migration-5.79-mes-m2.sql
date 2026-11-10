-- =====================================================================
-- Wave 5.79 M2：MES 物料与追溯 权限/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.79-mes-m2.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : whale-mes M2（条码规则与生成、批次与谱系正反追溯、扫码防错、线边仓与叫料）。
-- 权限编号 : MES 预留 1100–1399；本脚本使用 1220–1225。
-- 菜单编号 : MES 预留 240–299；本脚本使用 260–266。
-- 授权口径 : 计划员 = 追溯查询 + 批次 + 线边仓 + 补货；
--            tenant_admin / mes_manager / mes_team_lead = 物料追溯全量；
--            mes_operator = 追溯查询 + 线边仓 + 补货 + 扫码防错；
--            mes_quality / mes_equipment / mes_member = 仅追溯查询。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 物料追溯权限 1220–1225
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1220, 0, 'sys:mes:trace:list', '查询 MES 条码/批次/追溯', 'API', '/mes/trace/barcode/page,/mes/trace/batch/page,/mes/trace/trace/forward,/mes/trace/trace/backward', 'GET', 1220, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1220 OR perm_code = 'sys:mes:trace:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1221, 0, 'sys:mes:trace:barcode', 'MES 条码规则与生成', 'API', '/mes/trace/barcode/generate,/mes/trace/barcode/{id}/void,/mes/trace/barcode/rule,/mes/trace/barcode/rule/page', 'POST', 1221, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1221 OR perm_code = 'sys:mes:trace:barcode');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1222, 0, 'sys:mes:trace:batch', 'MES 批次建档与谱系', 'API', '/mes/trace/batch,/mes/trace/batch/{id}/close,/mes/trace/trace/link', 'POST', 1222, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1222 OR perm_code = 'sys:mes:trace:batch');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1223, 0, 'sys:mes:trace:line-side', 'MES 线边仓出入库与流水', 'API', '/mes/trace/line-side/stock/page,/mes/trace/line-side/stock/inbound,/mes/trace/line-side/stock/issue,/mes/trace/line-side/movement/page', 'POST', 1223, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1223 OR perm_code = 'sys:mes:trace:line-side');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1224, 0, 'sys:mes:trace:replenish', 'MES 线边叫料补货', 'API', '/mes/trace/line-side/replenish,/mes/trace/line-side/replenish/page', 'POST', 1224, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1224 OR perm_code = 'sys:mes:trace:replenish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1225, 0, 'sys:mes:trace:scan', 'MES 扫码防错与规则', 'API', '/mes/trace/scan/validate,/mes/trace/scan/rule,/mes/trace/scan/rule/page', 'POST', 1225, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1225 OR perm_code = 'sys:mes:trace:scan');

-- ---------------------------------------------------------------------
-- 2. 菜单 260–266（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 260, 1, 0, '条码管理', '/mes/trace/barcode', 'mes/trace/barcode', 'barcode', 260, 'MENU', 'sys:mes:trace:barcode', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 260);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 261, 1, 0, '批次管理', '/mes/trace/batch', 'mes/trace/batch', 'cluster', 261, 'MENU', 'sys:mes:trace:batch', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 261);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 262, 1, 0, '追溯查询', '/mes/trace/query', 'mes/trace/query', 'node-index', 262, 'MENU', 'sys:mes:trace:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 262);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 263, 1, 0, '线边库存', '/mes/trace/line-side/stock', 'mes/trace/line-side/stock', 'database', 263, 'MENU', 'sys:mes:trace:line-side', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 263);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 264, 1, 0, '叫料补货', '/mes/trace/line-side/replenishment', 'mes/trace/line-side/replenishment', 'shopping-cart', 264, 'MENU', 'sys:mes:trace:replenish', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 264);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 265, 1, 0, '扫码防错', '/mes/trace/scan', 'mes/trace/scan', 'scan', 265, 'MENU', 'sys:mes:trace:scan', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 265);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 266, 1, 0, '扫码规则', '/mes/trace/scan-rule', 'mes/trace/scan-rule', 'setting', 266, 'MENU', 'sys:mes:trace:scan', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 266);

-- ---------------------------------------------------------------------
-- 3. 授权
-- ---------------------------------------------------------------------
-- 3.1 计划员：追溯查询 + 批次 + 线边仓 + 补货
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_planner'
        AND p.id IN (1220, 1222, 1223, 1224)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.2 tenant_admin / mes_manager / mes_team_lead：物料追溯全量
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'mes_manager', 'mes_team_lead')
        AND p.id BETWEEN 1220 AND 1225) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.3 操作工：追溯查询 + 线边仓 + 补货 + 扫码防错
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_operator'
        AND p.id IN (1220, 1223, 1224, 1225)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.4 质量员 / 设备员 / 普通成员：仅追溯查询
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_quality', 'mes_equipment', 'mes_member')
        AND p.id = 1220) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1220 AND 1225;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 260 AND 266;
--   SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     WHERE rp.permission_id BETWEEN 1220 AND 1225
--     GROUP BY r.role_code ORDER BY r.role_code;
-- =====================================================================
