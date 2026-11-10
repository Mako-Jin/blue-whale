-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.35：工作流转办/委派/回退 + 表单数据修改权限点）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_permission 表的运行库
--            （保留存量权限与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 4 行）。
-- 说明     : 本脚本幂等（NOT EXISTS 幂等插入 按主键去重）。新增权限点：
--            · sys:workflow:task:transfer  → PUT  /workflow/task/{id}/transfer
--            · sys:workflow:task:delegate  → PUT  /workflow/task/{id}/delegate|resolve
--            · sys:workflow:task:rollback  → POST /workflow/task/{id}/rollback
--            · sys:form:data:edit          → PUT  /form/data/{id}
--           授权仍由角色分配界面完成，本脚本不写 sys_role_permission。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 74, 0, 'sys:workflow:task:transfer', '转办待办任务', 'API', '/workflow/task/{id}/transfer', 'PUT', 74, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 74 OR perm_code = 'sys:workflow:task:transfer');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 75, 0, 'sys:workflow:task:delegate', '委派待办任务', 'API', '/workflow/task/{id}/{delegate|resolve}', 'PUT', 75, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 75 OR perm_code = 'sys:workflow:task:delegate');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 76, 0, 'sys:workflow:task:rollback', '回退待办任务', 'API', '/workflow/task/{id}/rollback', 'POST', 76, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 76 OR perm_code = 'sys:workflow:task:rollback');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 77, 0, 'sys:form:data:edit', '修改表单数据', 'API', '/form/data/{id}', 'PUT', 77, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 77 OR perm_code = 'sys:form:data:edit');
