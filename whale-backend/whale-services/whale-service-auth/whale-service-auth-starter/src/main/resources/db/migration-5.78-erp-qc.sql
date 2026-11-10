-- =====================================================================
-- Wave 5.78 M5：ERP 质量与设备权限/角色/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-qc.sql
-- 幂等说明 : 权限、角色、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 模块化单体内的 QC 上下文（IQC/IPQC/FQC 检验与判定、
--            不合格品处置与质量成本、设备档案/保养计划/保养工单/OEE、
--            量具台账/校准计划/校准记录），本脚本登记权限点、角色与菜单。
-- 权限编号 : 已用至 474（M4 绩效关闭），本脚本使用 475–494。
-- 菜单编号 : 已用至 90（M4 绩效管理），本脚本使用 91–93。
-- 角色编号 : 平台角色 25115 erp_qc；业务租户复制角色与授权 ID 采用
--            「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : tenant_admin / erp_manager / erp_qc 获得 475–494（20 项）；
--            erp_member 仅获得查询 475/479/482/485/488/490/494；
--            erp_finance 仅获得 494（质量成本只读）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 检验与判定权限 475–478
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 475, 0, 'sys:erp:qc:inspection:list', '查询质量检验单', 'API', '/erp/qc/inspection/page,/erp/qc/inspection/{id}', 'GET', 475, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 475 OR perm_code = 'sys:erp:qc:inspection:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 476, 0, 'sys:erp:qc:inspection:add', '新增质量检验单', 'API', '/erp/qc/inspection', 'POST', 476, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 476 OR perm_code = 'sys:erp:qc:inspection:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 477, 0, 'sys:erp:qc:inspection:record', '录入检验项', 'API', '/erp/qc/inspection/{id}/record', 'POST', 477, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 477 OR perm_code = 'sys:erp:qc:inspection:record');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 478, 0, 'sys:erp:qc:inspection:judge', '判定质量检验单', 'API', '/erp/qc/inspection/{id}/judge', 'POST', 478, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 478 OR perm_code = 'sys:erp:qc:inspection:judge');

-- ---------------------------------------------------------------------
-- 2. 不合格品与质量成本权限 479–481、494
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 479, 0, 'sys:erp:qc:ncr:list', '查询不合格品单', 'API', '/erp/qc/ncr/page', 'GET', 479, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 479 OR perm_code = 'sys:erp:qc:ncr:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 480, 0, 'sys:erp:qc:ncr:dispose', '登记/处置不合格品', 'API', '/erp/qc/ncr,/erp/qc/ncr/{id}/dispose', 'POST', 480, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 480 OR perm_code = 'sys:erp:qc:ncr:dispose');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 481, 0, 'sys:erp:qc:ncr:close', '关闭不合格品单', 'API', '/erp/qc/ncr/{id}/close', 'POST', 481, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 481 OR perm_code = 'sys:erp:qc:ncr:close');

-- ---------------------------------------------------------------------
-- 3. 设备档案权限 482–484
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 482, 0, 'sys:erp:qc:equipment:list', '查询设备档案', 'API', '/erp/qc/equipment/page', 'GET', 482, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 482 OR perm_code = 'sys:erp:qc:equipment:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 483, 0, 'sys:erp:qc:equipment:add', '新增设备档案', 'API', '/erp/qc/equipment', 'POST', 483, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 483 OR perm_code = 'sys:erp:qc:equipment:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 484, 0, 'sys:erp:qc:equipment:update', '编辑设备档案', 'API', '/erp/qc/equipment/{equipmentNo}', 'PUT', 484, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 484 OR perm_code = 'sys:erp:qc:equipment:update');

-- ---------------------------------------------------------------------
-- 4. 保养与 OEE 权限 485–489
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 485, 0, 'sys:erp:qc:maintenance:list', '查询保养计划与工单', 'API', '/erp/qc/maintenance/plan/page,/erp/qc/maintenance/order/page', 'GET', 485, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 485 OR perm_code = 'sys:erp:qc:maintenance:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 486, 0, 'sys:erp:qc:maintenance:save', '维护保养计划与工单', 'API', '/erp/qc/maintenance/plan,/erp/qc/maintenance/plan/{id},/erp/qc/maintenance/order', 'POST', 486, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 486 OR perm_code = 'sys:erp:qc:maintenance:save');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 487, 0, 'sys:erp:qc:maintenance:complete', '完成保养工单', 'API', '/erp/qc/maintenance/order/{id}/complete', 'POST', 487, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 487 OR perm_code = 'sys:erp:qc:maintenance:complete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 488, 0, 'sys:erp:qc:oee:list', '查询设备 OEE', 'API', '/erp/qc/oee/page', 'GET', 488, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 488 OR perm_code = 'sys:erp:qc:oee:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 489, 0, 'sys:erp:qc:oee:record', '回传设备 OEE', 'API', '/erp/qc/oee/record', 'POST', 489, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 489 OR perm_code = 'sys:erp:qc:oee:record');

-- ---------------------------------------------------------------------
-- 5. 量具与校准权限 490–493
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 490, 0, 'sys:erp:qc:metrology:list', '查询量具与校准', 'API', '/erp/qc/metrology/tool/page,/erp/qc/metrology/plan/page,/erp/qc/metrology/record/page', 'GET', 490, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 490 OR perm_code = 'sys:erp:qc:metrology:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 491, 0, 'sys:erp:qc:metrology:add', '新增量具与校准计划', 'API', '/erp/qc/metrology/tool,/erp/qc/metrology/plan', 'POST', 491, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 491 OR perm_code = 'sys:erp:qc:metrology:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 492, 0, 'sys:erp:qc:metrology:update', '编辑量具与校准计划', 'API', '/erp/qc/metrology/tool/{toolNo},/erp/qc/metrology/plan/{id}', 'PUT', 492, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 492 OR perm_code = 'sys:erp:qc:metrology:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 493, 0, 'sys:erp:qc:metrology:calibrate', '登记量具校准', 'API', '/erp/qc/metrology/record', 'POST', 493, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 493 OR perm_code = 'sys:erp:qc:metrology:calibrate');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 494, 0, 'sys:erp:qc:quality-cost:list', '查询质量成本', 'API', '/erp/qc/quality-cost/page', 'GET', 494, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 494 OR perm_code = 'sys:erp:qc:quality-cost:list');

-- ---------------------------------------------------------------------
-- 6. 平台租户业务角色模板 25115 erp_qc
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25115, 1, 'erp_qc', 'ERP 质量', '质量检验与不合格品、设备与保养、量具与校准、质量成本', 265, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25115 OR (tenant_id = 1 AND role_code = 'erp_qc'));

-- ---------------------------------------------------------------------
-- 7. 既有业务租户复制 ERP 质量角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'erp_qc' AS role_code, 'ERP 质量' AS role_name, '质量检验与不合格品、设备与保养、量具与校准、质量成本' AS description, 265 AS sort_no ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 8. 授权
--    tenant_admin / erp_manager / erp_qc：475–494
--    erp_member：475/479/482/485/488/490/494（只读）
--    erp_finance：494（质量成本只读）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 475 AND 494 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_qc')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (475, 479, 482, 485, 488, 490, 494) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (494) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_finance')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 9. 菜单 91–93（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 91, 1, 0, '检验与不合格品', '/erp/qc/inspection', 'erp/qc/inspection', 'audit', 91, 'MENU', 'sys:erp:qc:inspection:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 91);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 92, 1, 0, '设备与保养', '/erp/qc/equipment', 'erp/qc/equipment', 'tool', 92, 'MENU', 'sys:erp:qc:equipment:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 92);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 93, 1, 0, '量具与校准', '/erp/qc/metrology', 'erp/qc/metrology', 'aim', 93, 'MENU', 'sys:erp:qc:metrology:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 93);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 475 AND 494;
--   SELECT id, role_code, role_name FROM sys_role WHERE role_code = 'erp_qc';
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 91 AND 93;
-- =====================================================================
