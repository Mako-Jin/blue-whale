-- =====================================================================
-- Wave 5.79 M3：MES 车间质量 权限/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.79-mes-m3.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : whale-mes M3（检验计划、首件/IPQC、NCR、处置、返工、SPC、质量看板）。
-- 权限编号 : MES 预留 1100–1399；本脚本使用 1260–1266。
-- 菜单编号 : MES 预留 240–299；本脚本使用 267–273。
-- 授权口径 : mes_quality / tenant_admin / mes_manager / mes_team_lead = 质量全量；
--            mes_planner = 质量查询 + 检验计划 + 首件/IPQC；
--            mes_operator = 质量查询 + 首件/IPQC + 返工 + SPC；
--            mes_equipment / mes_member = 仅质量查询。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 质量权限 1260–1266
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1260, 0, 'sys:mes:quality:list', '查询 MES 质量数据', 'API', '/mes/quality/inspection-plan/page,/mes/quality/inspection/task/page,/mes/quality/inspection/task/{id},/mes/quality/inspection/first-article/gate,/mes/quality/ncr/page,/mes/quality/disposition/page,/mes/quality/rework/page,/mes/quality/spc/rule/page,/mes/quality/spc/sample/page,/mes/quality/spc/chart,/mes/quality/board', 'GET', 1260, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1260 OR perm_code = 'sys:mes:quality:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1261, 0, 'sys:mes:quality:plan', 'MES 检验计划维护', 'API', '/mes/quality/inspection-plan', 'POST', 1261, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1261 OR perm_code = 'sys:mes:quality:plan');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1262, 0, 'sys:mes:quality:inspection', 'MES 首件/IPQC 检验', 'API', '/mes/quality/inspection/task,/mes/quality/inspection/task/{id}/results,/mes/quality/inspection/task/{id}/judge', 'POST', 1262, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1262 OR perm_code = 'sys:mes:quality:inspection');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1263, 0, 'sys:mes:quality:ncr', 'MES 不合格品 NCR', 'API', '/mes/quality/ncr,/mes/quality/ncr/{id}/close', 'POST', 1263, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1263 OR perm_code = 'sys:mes:quality:ncr');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1264, 0, 'sys:mes:quality:disposition', 'MES 不合格品处置', 'API', '/mes/quality/disposition,/mes/quality/disposition/{id}/execute', 'POST', 1264, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1264 OR perm_code = 'sys:mes:quality:disposition');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1265, 0, 'sys:mes:quality:rework', 'MES 返工单执行', 'API', '/mes/quality/rework/{id}/start,/mes/quality/rework/{id}/complete', 'POST', 1265, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1265 OR perm_code = 'sys:mes:quality:rework');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1266, 0, 'sys:mes:quality:spc', 'MES SPC 规则与样本', 'API', '/mes/quality/spc/rule,/mes/quality/spc/sample', 'POST', 1266, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1266 OR perm_code = 'sys:mes:quality:spc');

-- ---------------------------------------------------------------------
-- 2. 菜单 267–273（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 267, 1, 0, '首件检验', '/mes/quality/first-article', 'mes/quality/first-article', 'safety', 267, 'MENU', 'sys:mes:quality:inspection', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 267);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 268, 1, 0, 'IPQC 巡检', '/mes/quality/ipqc', 'mes/quality/ipqc', 'carry-out', 268, 'MENU', 'sys:mes:quality:inspection', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 268);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 269, 1, 0, '不良品管理', '/mes/quality/ncr', 'mes/quality/ncr', 'warning', 269, 'MENU', 'sys:mes:quality:ncr', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 269);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 270, 1, 0, '处置管理', '/mes/quality/disposition', 'mes/quality/disposition', 'tool', 270, 'MENU', 'sys:mes:quality:disposition', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 270);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 271, 1, 0, '返工管理', '/mes/quality/rework', 'mes/quality/rework', 'redo', 271, 'MENU', 'sys:mes:quality:rework', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 271);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 272, 1, 0, 'SPC 控制', '/mes/quality/spc', 'mes/quality/spc', 'line-chart', 272, 'MENU', 'sys:mes:quality:spc', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 272);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 273, 1, 0, '质量看板', '/mes/quality/board', 'mes/quality/board', 'dashboard', 273, 'MENU', 'sys:mes:quality:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 273);

-- ---------------------------------------------------------------------
-- 3. 授权
-- ---------------------------------------------------------------------
-- 3.1 质量员 / 管理员 / 厂长 / 班组长：质量全量
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_quality', 'tenant_admin', 'mes_manager', 'mes_team_lead')
        AND p.id BETWEEN 1260 AND 1266) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.2 计划员：查询 + 检验计划 + 首件/IPQC
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_planner'
        AND p.id IN (1260, 1261, 1262)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.3 操作工：查询 + 首件/IPQC + 返工 + SPC
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_operator'
        AND p.id IN (1260, 1262, 1265, 1266)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.4 设备员 / 普通成员：仅质量查询
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_equipment', 'mes_member')
        AND p.id = 1260) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1260 AND 1266;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 267 AND 273;
--   SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     WHERE rp.permission_id BETWEEN 1260 AND 1266
--     GROUP BY r.role_code ORDER BY r.role_code;
-- =====================================================================
