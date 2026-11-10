-- =====================================================================
-- Wave 5.82 M2-5b：OA 报销申请 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m2-expense.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增报销单、费用类别与费用标准，审批通过后推送 ERP 应付单。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1690–1697。
-- 菜单编号 : OA 段 360–409；本脚本使用 377–379。
-- 授权口径 : oa_admin、oa_hr_admin、oa_finance、tenant_admin 获得 1690–1697 全量；
--            oa_manager 获得 1690–1694、1696；oa_member 获得 1690/1691/1694/1696。
-- 前置条件 : bluewhale_oa 已执行 V8__expense_admin.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1690–1697
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1690, 0, 'sys:oa:expense:list', '查询报销单', 'API', '/oa/expense/page,/oa/expense/{id}', 'GET', 1690, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1690 OR perm_code = 'sys:oa:expense:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1691, 0, 'sys:oa:expense:add', '新建报销单', 'API', '/oa/expense', 'POST', 1691, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1691 OR perm_code = 'sys:oa:expense:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1692, 0, 'sys:oa:expense:edit', '更新报销单', 'API', '/oa/expense/{id}', 'PUT', 1692, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1692 OR perm_code = 'sys:oa:expense:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1693, 0, 'sys:oa:expense:delete', '删除报销单', 'API', '/oa/expense/{id}', 'DELETE', 1693, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1693 OR perm_code = 'sys:oa:expense:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1694, 0, 'sys:oa:expense:submit', '提交报销单', 'API',
       '/oa/expense/{id}/submit,/oa/expense/{id}/status', 'POST', 1694, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1694 OR perm_code = 'sys:oa:expense:submit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1695, 0, 'sys:oa:expense:push', '重试推送报销应付', 'API', '/oa/expense/{id}/push-retry', 'POST', 1695, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1695 OR perm_code = 'sys:oa:expense:push');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1696, 0, 'sys:oa:expense:category:list', '查询费用类别与标准', 'API',
       '/oa/expense/category,/oa/expense/category/{categoryId}/standards', 'GET', 1696, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1696 OR perm_code = 'sys:oa:expense:category:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1697, 0, 'sys:oa:expense:category:edit', '维护费用类别与标准', 'API',
       '/oa/expense/category,/oa/expense/category/{id},/oa/expense/category/standards,/oa/expense/category/standards/{id}',
       'POST', 1697, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1697 OR perm_code = 'sys:oa:expense:category:edit');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_hr_admin', 'oa_finance', 'tenant_admin') AND p.id BETWEEN 1690 AND 1697) OR (r.role_code = 'oa_manager' AND p.id IN (1690, 1691, 1692, 1693, 1694, 1696)) OR (r.role_code = 'oa_member' AND p.id IN (1690, 1691, 1694, 1696)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 377–379（OA 根菜单尚未落地，暂挂顶级 parent_id = 0）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 377, 1, 0, '报销申请', '/oa/expense/list', 'oa/expense/list/index', 'account-book', 377, 'MENU', 'sys:oa:expense:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 377);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 378, 1, 0, '报销配置', '/oa/expense/category', 'oa/expense/category/index', 'setting', 378, 'MENU', 'sys:oa:expense:category:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 378);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 379, 1, 0, '报销审批', '/oa/expense/approval', 'oa/expense/approval/index', 'audit', 379, 'MENU', 'sys:oa:expense:submit', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 379);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1690 AND 1697 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('oa_admin','oa_hr_admin','oa_finance','oa_manager','oa_member')
--   GROUP BY r.role_code;
--   -- 期望：oa_admin/oa_hr_admin/oa_finance ≥8、oa_manager ≥6、oa_member ≥4（在既有授权基础上累加）。
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 377 AND 379;
-- =====================================================================
