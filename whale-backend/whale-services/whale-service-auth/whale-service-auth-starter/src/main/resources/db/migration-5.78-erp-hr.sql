-- =====================================================================
-- Wave 5.78 M4：ERP 轻量人力权限/角色/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.78-erp-hr.sql
-- 幂等说明 : 权限、角色、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-erp 模块化单体内的 HR 上下文（组织/员工档案、异动、考勤汇总、
--            薪资批次与工资条、绩效周期与考核），薪资审批/发放生成计提与发放凭证；
--            本脚本登记权限点、角色与菜单。
-- 权限编号 : 已用至 455（生产订单），本脚本使用 456–474。
-- 菜单编号 : 已用至 86（生产订单），本脚本使用 87–90。
-- 角色编号 : 平台角色 25114 erp_hr；业务租户复制角色与授权 ID 采用
--            「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : tenant_admin / erp_manager / erp_hr 获得 456–474（19 项）；
--            erp_member 仅获得查询 456/459/463/465/470/471；
--            erp_finance 仅获得 465/470（薪资与工资条只读）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 组织单元权限 456–458
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 456, 0, 'sys:erp:hr:org:list', '查询组织单元', 'API', '/erp/hr/org', 'GET', 456, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 456 OR perm_code = 'sys:erp:hr:org:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 457, 0, 'sys:erp:hr:org:add', '新增组织单元', 'API', '/erp/hr/org', 'POST', 457, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 457 OR perm_code = 'sys:erp:hr:org:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 458, 0, 'sys:erp:hr:org:update', '编辑组织单元', 'API', '/erp/hr/org/{code}', 'PUT', 458, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 458 OR perm_code = 'sys:erp:hr:org:update');

-- ---------------------------------------------------------------------
-- 2. 员工档案与异动权限 459–462
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 459, 0, 'sys:erp:hr:employee:list', '查询员工档案', 'API', '/erp/hr/employee/page,/erp/hr/employee/change/page,/erp/hr/employee/{employeeNo}', 'GET', 459, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 459 OR perm_code = 'sys:erp:hr:employee:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 460, 0, 'sys:erp:hr:employee:add', '新增员工档案', 'API', '/erp/hr/employee', 'POST', 460, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 460 OR perm_code = 'sys:erp:hr:employee:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 461, 0, 'sys:erp:hr:employee:update', '编辑员工档案', 'API', '/erp/hr/employee/{employeeNo}', 'PUT', 461, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 461 OR perm_code = 'sys:erp:hr:employee:update');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 462, 0, 'sys:erp:hr:employee:change', '员工异动', 'API', '/erp/hr/employee/change,/erp/hr/employee/change/{id}/effective', 'POST', 462, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 462 OR perm_code = 'sys:erp:hr:employee:change');

-- ---------------------------------------------------------------------
-- 3. 考勤汇总权限 463–464
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 463, 0, 'sys:erp:hr:attendance:list', '查询考勤汇总', 'API', '/erp/hr/attendance/page', 'GET', 463, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 463 OR perm_code = 'sys:erp:hr:attendance:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 464, 0, 'sys:erp:hr:attendance:save', '登记考勤汇总', 'API', '/erp/hr/attendance', 'POST', 464, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 464 OR perm_code = 'sys:erp:hr:attendance:save');

-- ---------------------------------------------------------------------
-- 4. 薪资批次与工资条权限 465–470
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 465, 0, 'sys:erp:hr:payroll:list', '查询薪资批次', 'API', '/erp/hr/payroll/page,/erp/hr/payroll/{id}', 'GET', 465, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 465 OR perm_code = 'sys:erp:hr:payroll:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 466, 0, 'sys:erp:hr:payroll:create', '新建/取消薪资批次', 'API', '/erp/hr/payroll,/erp/hr/payroll/{id}/cancel', 'POST', 466, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 466 OR perm_code = 'sys:erp:hr:payroll:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 467, 0, 'sys:erp:hr:payroll:calculate', '计算薪资', 'API', '/erp/hr/payroll/{id}/calculate', 'POST', 467, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 467 OR perm_code = 'sys:erp:hr:payroll:calculate');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 468, 0, 'sys:erp:hr:payroll:approve', '审批/结账薪资', 'API', '/erp/hr/payroll/{id}/approve,/erp/hr/payroll/{id}/close', 'POST', 468, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 468 OR perm_code = 'sys:erp:hr:payroll:approve');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 469, 0, 'sys:erp:hr:payroll:pay', '发放薪资', 'API', '/erp/hr/payroll/{id}/pay', 'POST', 469, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 469 OR perm_code = 'sys:erp:hr:payroll:pay');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 470, 0, 'sys:erp:hr:payslip:list', '查询我的工资条', 'API', '/erp/hr/payroll/payslip/my', 'GET', 470, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 470 OR perm_code = 'sys:erp:hr:payslip:list');

-- ---------------------------------------------------------------------
-- 5. 绩效管理权限 471–474
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 471, 0, 'sys:erp:hr:performance:list', '查询绩效周期', 'API', '/erp/hr/performance/page,/erp/hr/performance/{id}', 'GET', 471, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 471 OR perm_code = 'sys:erp:hr:performance:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 472, 0, 'sys:erp:hr:performance:create', '新建绩效周期', 'API', '/erp/hr/performance', 'POST', 472, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 472 OR perm_code = 'sys:erp:hr:performance:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 473, 0, 'sys:erp:hr:performance:appraise', '绩效指标与考核', 'API', '/erp/hr/performance/{id}/kpi,/erp/hr/performance/{id}/appraise', 'POST', 473, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 473 OR perm_code = 'sys:erp:hr:performance:appraise');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 474, 0, 'sys:erp:hr:performance:close', '关闭绩效周期', 'API', '/erp/hr/performance/{id}/close', 'POST', 474, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 474 OR perm_code = 'sys:erp:hr:performance:close');

-- ---------------------------------------------------------------------
-- 6. 平台租户业务角色模板 25114 erp_hr
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25114, 1, 'erp_hr', 'ERP 人力', '组织与员工档案、异动、考勤汇总、薪资批次与绩效管理', 264, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25114 OR (tenant_id = 1 AND role_code = 'erp_hr'));

-- ---------------------------------------------------------------------
-- 7. 既有业务租户复制 ERP 人力角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'erp_hr' AS role_code, 'ERP 人力' AS role_name, '组织与员工档案、异动、考勤汇总、薪资批次与绩效管理' AS description, 264 AS sort_no ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 8. 授权
--    tenant_admin / erp_manager / erp_hr：456–474
--    erp_member：456/459/463/465/470/471（只读）
--    erp_finance：465/470（薪资与工资条只读）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 456 AND 474 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'erp_manager', 'erp_hr')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (456, 459, 463, 465, 470, 471) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_member')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (465, 470) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('erp_finance')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 9. 菜单 87–90（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 87, 1, 0, '组织与员工', '/erp/hr/employee', 'erp/hr/employee', 'team', 87, 'MENU', 'sys:erp:hr:employee:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 87);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 88, 1, 0, '考勤汇总', '/erp/hr/attendance', 'erp/hr/attendance', 'schedule', 88, 'MENU', 'sys:erp:hr:attendance:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 88);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 89, 1, 0, '薪资批次', '/erp/hr/payroll', 'erp/hr/payroll', 'money-collect', 89, 'MENU', 'sys:erp:hr:payroll:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 89);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 90, 1, 0, '绩效管理', '/erp/hr/performance', 'erp/hr/performance', 'award', 90, 'MENU', 'sys:erp:hr:performance:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 90);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 456 AND 474;
--   SELECT id, role_code, role_name FROM sys_role WHERE role_code = 'erp_hr';
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 87 AND 90;
-- =====================================================================
