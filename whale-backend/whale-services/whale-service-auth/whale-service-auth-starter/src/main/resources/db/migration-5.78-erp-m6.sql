-- =====================================================================
-- Wave 5.78 M6：ERP 数据分析与报表中心权限/角色/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-m6.sql
-- 幂等说明 : 权限、角色、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 模块化单体内的 Analytics 上下文（经营驾驶舱、多维分析、
--            报表定义/定时计划/导出、预警规则/事件/评估处置），本脚本登记权限点、
--            角色与菜单。
-- 权限编号 : 已用至 494（M5 量具校准），本脚本使用 495–506；507–514 预留。
-- 菜单编号 : 已用至 93（M5 量具），本脚本使用 94–97。
-- 角色编号 : 平台角色 25116 erp_bi；业务租户复制角色与授权 ID 采用
--            「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : tenant_admin / erp_manager / erp_bi 获得 495–506（12 项）；
--            erp_finance 获得 495/497/498/500/501（驾驶舱与报表查看/导出）；
--            erp_member 获得 495/497/498/500/503（驾驶舱、多维、报表执行与预警只读）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 驾驶舱权限 495–496
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 495, 0, 'sys:erp:bi:cockpit:list', '查询经营驾驶舱', 'API', '/erp/bi/cockpit', 'GET', 495, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 495 OR perm_code = 'sys:erp:bi:cockpit:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 496, 0, 'sys:erp:bi:cockpit:refresh', '刷新/失效驾驶舱快照', 'API', '/erp/bi/cockpit/refresh,/erp/bi/cockpit/invalidate', 'POST', 496, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 496 OR perm_code = 'sys:erp:bi:cockpit:refresh');

-- ---------------------------------------------------------------------
-- 2. 多维分析权限 497
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 497, 0, 'sys:erp:bi:cube:query', '多维分析查询', 'API', '/erp/bi/cube', 'GET', 497, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 497 OR perm_code = 'sys:erp:bi:cube:query');

-- ---------------------------------------------------------------------
-- 3. 报表中心权限 498–502
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 498, 0, 'sys:erp:bi:report:list', '查询报表定义与计划', 'API', '/erp/bi/report/definition/page,/erp/bi/report/schedule/page', 'GET', 498, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 498 OR perm_code = 'sys:erp:bi:report:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 499, 0, 'sys:erp:bi:report:save', '维护报表定义与计划', 'API', '/erp/bi/report/definition,/erp/bi/report/definition/{reportNo},/erp/bi/report/schedule,/erp/bi/report/schedule/{scheduleNo}', 'POST', 499, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 499 OR perm_code = 'sys:erp:bi:report:save');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 500, 0, 'sys:erp:bi:report:execute', '执行报表', 'API', '/erp/bi/report/{reportNo}/execute', 'POST', 500, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 500 OR perm_code = 'sys:erp:bi:report:execute');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 501, 0, 'sys:erp:bi:report:export', '导出报表', 'API', '/erp/bi/report/{reportNo}/export', 'GET', 501, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 501 OR perm_code = 'sys:erp:bi:report:export');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 502, 0, 'sys:erp:bi:report:run', '手动执行报表计划', 'API', '/erp/bi/report/schedule/{scheduleNo}/run', 'POST', 502, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 502 OR perm_code = 'sys:erp:bi:report:run');

-- ---------------------------------------------------------------------
-- 4. 预警中心权限 503–506
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 503, 0, 'sys:erp:bi:alert:list', '查询预警规则与事件', 'API', '/erp/bi/alert/rule/page,/erp/bi/alert/event/page', 'GET', 503, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 503 OR perm_code = 'sys:erp:bi:alert:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 504, 0, 'sys:erp:bi:alert:save', '维护预警规则', 'API', '/erp/bi/alert/rule,/erp/bi/alert/rule/{ruleNo}', 'POST', 504, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 504 OR perm_code = 'sys:erp:bi:alert:save');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 505, 0, 'sys:erp:bi:alert:evaluate', '评估预警规则', 'API', '/erp/bi/alert/evaluate', 'POST', 505, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 505 OR perm_code = 'sys:erp:bi:alert:evaluate');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 506, 0, 'sys:erp:bi:alert:handle', '确认/关闭预警事件', 'API', '/erp/bi/alert/event/{id}/acknowledge,/erp/bi/alert/event/{id}/close', 'POST', 506, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 506 OR perm_code = 'sys:erp:bi:alert:handle');

-- ---------------------------------------------------------------------
-- 5. 平台租户业务角色模板 25116 erp_bi
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25116, 1, 'erp_bi', 'ERP 分析', '经营驾驶舱、多维分析、报表中心、预警中心', 266, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25116 OR (tenant_id = 1 AND role_code = 'erp_bi'));

-- ---------------------------------------------------------------------
-- 6. 既有业务租户复制 ERP 分析角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'erp_bi' AS role_code, 'ERP 分析' AS role_name, '经营驾驶舱、多维分析、报表中心、预警中心' AS description, 266 AS sort_no ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 7. 授权
--    tenant_admin / erp_manager / erp_bi：495–506
--    erp_finance：495/497/498/500/501（驾驶舱与报表查看、导出）
--    erp_member：495/497/498/500/503（驾驶舱、多维、报表执行、预警只读）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 495 AND 506 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_bi')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (495, 497, 498, 500, 501) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_finance')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (495, 497, 498, 500, 503) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 8. 菜单 94–97（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 94, 1, 0, '经营驾驶舱', '/erp/bi/cockpit', 'erp/bi/cockpit', 'dashboard', 94, 'MENU', 'sys:erp:bi:cockpit:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 94);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 95, 1, 0, '多维分析', '/erp/bi/cube', 'erp/bi/cube', 'pie-chart', 95, 'MENU', 'sys:erp:bi:cube:query', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 95);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 96, 1, 0, '报表中心', '/erp/bi/report', 'erp/bi/report', 'file-text', 96, 'MENU', 'sys:erp:bi:report:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 96);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 97, 1, 0, '预警中心', '/erp/bi/alert', 'erp/bi/alert', 'bell', 97, 'MENU', 'sys:erp:bi:alert:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 97);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 495 AND 506;
--   SELECT id, role_code, role_name FROM sys_role WHERE role_code = 'erp_bi';
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 94 AND 97;
-- =====================================================================
