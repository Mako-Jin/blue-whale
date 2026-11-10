-- =====================================================================
-- Wave 5.82 M3-P2：OA 通讯录与工作台 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m3p2-directory-workbench.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增内网通讯录代理查询与一次性工作台汇总接口。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1746–1747。
-- 菜单编号 : OA 段 360–409；本脚本使用 388–389。
-- 授权口径 : oa_admin、oa_manager、tenant_admin 与 oa_member 均获得 1746–1747
--            （通讯录与工作台为全员基础能力）。
-- 前置条件 : 无新增业务表，仅依赖 Auth 通讯录内网接口。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1746–1747
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1746, 0, 'sys:oa:directory:view', '查询通讯录', 'API',
       '/oa/directory/users,/oa/directory/depts', 'GET', 1746, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1746 OR perm_code = 'sys:oa:directory:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1747, 0, 'sys:oa:workbench:view', '查看工作台', 'API',
       '/oa/workbench/summary', 'GET', 1747, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1747 OR perm_code = 'sys:oa:workbench:view');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_manager', 'tenant_admin', 'oa_member') AND p.id BETWEEN 1746 AND 1747) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 388–389
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 388, 1, 0, '通讯录', '/oa/collab/directory', 'collab/directory/index', 'contacts', 388, 'MENU', 'sys:oa:directory:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 388);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 389, 1, 0, '工作台', '/oa/collab/workbench', 'collab/workbench/index', 'dashboard', 389, 'MENU', 'sys:oa:workbench:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 389);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1746 AND 1747 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id IN (388, 389) ORDER BY id;
-- =====================================================================
