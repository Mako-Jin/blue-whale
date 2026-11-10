-- =====================================================================
-- Wave 5.82 M6：OA 数据报表试点 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m6-report.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增报表中心（9 类报表 + 月度快照 + CSV 导出）。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1790–1792。
-- 菜单编号 : OA 段 360–409；本脚本使用 398。
-- 授权口径 : oa_admin、tenant_admin 获全量 1790–1792；
--            oa_manager 获全量（报表仅管理角色可见）；
--            oa_member 不授权（报表含人员与部门敏感数据）。
-- 前置条件 : whale-oa V15__report.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1790–1792
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1790, 0, 'sys:oa:report:list', '查看数据报表', 'API',
       '/oa/report/catalog,/oa/report/snapshots/page,/oa/report/snapshots/{id}', 'GET', 1790, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1790 OR perm_code = 'sys:oa:report:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1791, 0, 'sys:oa:report:manage', '生成报表快照', 'API',
       '/oa/report/{code}/snapshot', 'POST', 1791, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1791 OR perm_code = 'sys:oa:report:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1792, 0, 'sys:oa:report:export', '导出报表', 'API',
       '/oa/report/{code}/export', 'GET', 1792, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1792 OR perm_code = 'sys:oa:report:export');

-- ---------------------------------------------------------------------
-- 2. 授权（报表仅管理角色可见）
-- ---------------------------------------------------------------------
-- 2.1 全量：oa_admin、tenant_admin
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'tenant_admin') AND p.id BETWEEN 1790 AND 1792) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- 2.2 审批管理：oa_manager
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'oa_manager' AND p.id BETWEEN 1790 AND 1792) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 398 数据报表
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 398, 1, 0, '数据报表', '/oa/report', 'report/index', 'pie-chart', 398, 'MENU', 'sys:oa:report:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 398);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id BETWEEN 1790 AND 1792 ORDER BY id;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id = 398;
-- =====================================================================
