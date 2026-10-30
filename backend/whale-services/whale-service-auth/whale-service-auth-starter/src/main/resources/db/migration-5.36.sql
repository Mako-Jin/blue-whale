-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.36：工作流加签权限点）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_permission 表的运行库
--            （保留存量权限与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 1 行）。
-- 说明     : 本脚本幂等（NOT EXISTS 幂等插入 按主键去重）。新增权限点：
--            · sys:workflow:task:addsign → POST /workflow/task/{id}/add-sign
--            授权仍由角色分配界面完成（super_admin 默认拥有全部权限），
--            本脚本不写 sys_role_permission。
-- 修正     : 2026-10-26 权限码由 add-sign 改为 addsign（连字符违反
--            PermCode「冒号分隔的小写字母数字段落」格式，导致 /auth/me 400）；
--            末尾 UPDATE 用于修正存量运行库，幂等可重跑。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 78, 0, 'sys:workflow:task:addsign', '加签待办任务', 'API', '/workflow/task/{id}/add-sign', 'POST', 78, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 78 OR perm_code = 'sys:workflow:task:addsign');

-- 修正存量库中已写入的连字符权限码（幂等）
UPDATE sys_permission SET perm_code = 'sys:workflow:task:addsign'
WHERE id = 78 AND perm_code = 'sys:workflow:task:add-sign';
