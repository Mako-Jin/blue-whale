-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.35：工作流转办/委派/回退 + 表单数据修改权限点）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_permission 表的运行库
--            （保留存量权限与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 4 行）。
-- 说明     : 本脚本幂等（INSERT IGNORE 按主键去重）。新增权限点：
--            · sys:workflow:task:transfer  → PUT  /workflow/task/{id}/transfer
--            · sys:workflow:task:delegate  → PUT  /workflow/task/{id}/delegate|resolve
--            · sys:workflow:task:rollback  → POST /workflow/task/{id}/rollback
--            · sys:form:data:edit          → PUT  /form/data/{id}
--           授权仍由角色分配界面完成，本脚本不写 sys_role_permission。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (74, 0, 'sys:workflow:task:transfer', '转办待办任务', 'API', '/workflow/task/{id}/transfer', 'PUT', 74, 1),
    (75, 0, 'sys:workflow:task:delegate', '委派待办任务', 'API', '/workflow/task/{id}/{delegate|resolve}', 'PUT', 75, 1),
    (76, 0, 'sys:workflow:task:rollback', '回退待办任务', 'API', '/workflow/task/{id}/rollback', 'POST', 76, 1),
    (77, 0, 'sys:form:data:edit', '修改表单数据', 'API', '/form/data/{id}', 'PUT', 77, 1);
