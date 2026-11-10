-- =====================================================================
-- Wave 5.78 Step 4：ERP ↔ MES 打通权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-mes.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp MFG 上下文新增 MES 对账页（分页/详情/重推/拉取/人工确认入库）；
--            内部回传接口 /erp/internal/** 不登记业务权限，由内网令牌保护。
-- 权限编号 : 已用至 553（生产增强），本脚本使用 554–557；558–569 预留。
-- 菜单编号 : 已用至 107（产能负荷），本脚本使用 108；109 起预留。
-- 角色     : 沿用 25113 erp_mfg，不新增角色。
-- 授权口径 : tenant_admin / erp_manager / erp_mfg 获得 554–557；
--            erp_member 仅授予 554（只读对账）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. MES 对账权限 554–557
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 554, 0, 'sys:erp:mes:reconcile:list', 'MES 对账查询', 'API', '/erp/mfg/mes/reconcile/**', 'GET', 554, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 554 OR perm_code = 'sys:erp:mes:reconcile:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 555, 0, 'sys:erp:mes:push', 'MES 重推工单', 'API', '/erp/mfg/mes/reconcile/{orderId}/push', 'POST', 555, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 555 OR perm_code = 'sys:erp:mes:push');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 556, 0, 'sys:erp:mes:pull', 'MES 拉取进度', 'API', '/erp/mfg/mes/reconcile/{orderId}/pull', 'POST', 556, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 556 OR perm_code = 'sys:erp:mes:pull');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 557, 0, 'sys:erp:mes:complete', 'MES 人工确认入库', 'API', '/erp/mfg/mes/reconcile/{orderId}/completion-apply', 'POST', 557, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 557 OR perm_code = 'sys:erp:mes:complete');

-- ---------------------------------------------------------------------
-- 2. 授权：tenant_admin / erp_manager / erp_mfg：554–557
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 554 AND 557 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_mfg')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 授权：erp_member 仅 554（只读对账）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id = 554 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 4. 菜单 108（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 108, 1, 0, 'MES 对账', '/erp/mfg/mes-reconcile', 'erp/mes-reconcile', 'sync', 108, 'MENU', 'sys:erp:mes:reconcile:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 108);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 554 AND 557;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id = 108;
-- =====================================================================
