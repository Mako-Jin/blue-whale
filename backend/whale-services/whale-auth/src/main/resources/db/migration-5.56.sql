-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.56：权限码大小写合规修正）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_permission 表的运行库。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步修正）。
-- 背景     : PermCode 值对象要求「冒号分隔的全小写字母数字段落」，
--            以下两个历史种子为驼峰命名，导致 /auth/me 全量权限码加载 400：
--            · id=23 sys:user:resetPwd              → sys:user:resetpwd
--            · id=59 sys:workflow:definition:bpmnImport → sys:workflow:definition:bpmnimport
--            同步修正源码注解：UserController#changePassword、
--            ProcessModelController#importBpmn，及 admin 前端 UserList 按钮 perm。
-- 影响     : sys_role_permission 按 permission_id 外键关联，角色授权不受改名影响。
-- 说明     : 本脚本幂等（WHERE 条件匹配旧码才更新），可重复执行。
-- =====================================================================

UPDATE sys_permission SET perm_code = 'sys:user:resetpwd'
WHERE id = 23 AND perm_code = 'sys:user:resetPwd';

UPDATE sys_permission SET perm_code = 'sys:workflow:definition:bpmnimport'
WHERE id = 59 AND perm_code = 'sys:workflow:definition:bpmnImport';
