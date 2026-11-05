-- =====================================================================
-- Wave 5.80 M5：PDM 集成（CAD/ERP/MES/OA 同步与对账）权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-integration.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；不删除任何既有数据。
-- 背景     : whale-pdm 新增 CAD 检入/EBOM 导入、ERP 物料推送、BOM/MES/OA 对账与 OA 状态补偿。
-- 权限编号 : PDM 段 900–1099；本脚本使用 990–997。
-- 菜单编号 : PDM 段 180–219；本脚本使用 202–203。
-- 授权口径 : tenant_admin / pdm_manager 全量；pdm_engineer 查询+CAD 契约；pdm_member 仅查询日志。
-- 前置条件 : 网关已将 /erp/internal/** 与 /pdm/internal/** 列入 internal-deny。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 990–997
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 990, 0, 'sys:pdm:int:sync:list', '查询集成同步日志', 'API', '/pdm/integration/sync/page', 'GET', 990, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 990 OR perm_code = 'sys:pdm:int:sync:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 991, 0, 'sys:pdm:int:sync:retry', '重试集成同步', 'API', '/pdm/integration/sync/{id}/retry', 'POST', 991, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 991 OR perm_code = 'sys:pdm:int:sync:retry');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 992, 0, 'sys:pdm:int:reconcile:list', '查询集成对账结果', 'API', '/pdm/integration/reconcile/page', 'GET', 992, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 992 OR perm_code = 'sys:pdm:int:reconcile:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 993, 0, 'sys:pdm:int:reconcile:bom', '执行对账与重推补偿', 'API', '/pdm/integration/reconcile/bom/run,/pdm/integration/reconcile/route/run,/pdm/integration/reconcile/oa/run,/pdm/integration/reconcile/bom/{bomNo}/versions/{versionNo}/notify,/pdm/integration/reconcile/route/{routeId}/repush', 'POST', 993, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 993 OR perm_code = 'sys:pdm:int:reconcile:bom');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 994, 0, 'sys:pdm:int:oa:compensate', 'OA 审批状态补偿', 'API', '/pdm/integration/reconcile/oa/{bizType}/{bizId}/compensate', 'POST', 994, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 994 OR perm_code = 'sys:pdm:int:oa:compensate');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 995, 0, 'sys:pdm:int:cad:checkin', 'CAD 检入图纸', 'API', '/pdm/integration/cad/checkin', 'POST', 995, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 995 OR perm_code = 'sys:pdm:int:cad:checkin');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 996, 0, 'sys:pdm:int:cad:import', 'CAD 导入 EBOM', 'API', '/pdm/integration/cad/ebom-import', 'POST', 996, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 996 OR perm_code = 'sys:pdm:int:cad:import');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 997, 0, 'sys:pdm:int:erp:sync', '推送零件到 ERP', 'API', '/pdm/integration/erp/part/{partId}/sync', 'POST', 997, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 997 OR perm_code = 'sys:pdm:int:erp:sync');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager') AND p.id BETWEEN 990 AND 997) OR (r.role_code = 'pdm_engineer' AND p.id IN (990, 992, 995, 996)) OR (r.role_code = 'pdm_collab_manager' AND p.id IN (990, 992)) OR (r.role_code = 'pdm_member' AND p.id = 990) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 202, 1, 0, '同步记录', '/pdm/integration/sync', 'pdm/integration/sync/index', 'sync', 202, 'MENU', 'sys:pdm:int:sync:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 202);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 203, 1, 0, '集成对账', '/pdm/integration/reconcile', 'pdm/integration/reconcile/index', 'audit', 203, 'MENU', 'sys:pdm:int:reconcile:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 203);

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 990 AND 997 ORDER BY id;
--   -- 期望：8 行，990..997 连续。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_engineer','pdm_collab_manager','pdm_member')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager=8、pdm_engineer=4、pdm_collab_manager=2、pdm_member=1（另加既有权限）。
-- =====================================================================
