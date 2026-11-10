-- =====================================================================
-- Wave 5.82 M5-7d-2：OA 人员异动 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m5-hr-change.sql
-- 幂等说明 : 权限与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增人员异动（入职/转正/调岗/离职/返聘）审批闭环。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1788–1789。
-- 菜单     : OA 菜单段 395–397 已于 7d-1 用尽，398+ 预留 M6；
--            本批复用既有菜单 368「员工档案」(/oa/hr/profile)，不新增菜单行。
-- 授权口径 : oa_admin、tenant_admin、oa_manager 获全量 1788–1789；
--            oa_member 获 1788（查看本人/部门异动记录）。
-- 前置条件 : whale-oa V14__hr_change.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1788–1789
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1788, 0, 'sys:oa:hr:change:list', '查看人员异动', 'API',
       '/oa/hr/change/page,/oa/hr/change/{id}',
       'GET', 1788, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1788 OR perm_code = 'sys:oa:hr:change:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1789, 0, 'sys:oa:hr:change:manage', '维护人员异动', 'API',
       '/oa/hr/change,/oa/hr/change/{id},/oa/hr/change/{id}/submit,/oa/hr/change/{id}/execute,/oa/hr/change/{id}/tasks/{taskId}/done',
       'ALL', 1789, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1789 OR perm_code = 'sys:oa:hr:change:manage');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色分段）
-- ---------------------------------------------------------------------
-- 2.1 全量：oa_admin、tenant_admin
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'tenant_admin') AND p.id BETWEEN 1788 AND 1789) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- 2.2 人事管理：oa_manager
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'oa_manager' AND p.id BETWEEN 1788 AND 1789) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- 2.3 普通员工：oa_member（只读）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'oa_member' AND p.id IN (1788)) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id BETWEEN 1788 AND 1789 ORDER BY id;
-- =====================================================================
