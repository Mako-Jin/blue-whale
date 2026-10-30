-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.52：WebIM / 智能体权限点）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_permission 表的运行库
--            （保留存量权限与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这些权限点）。
-- 说明     : 本脚本幂等（NOT EXISTS 幂等插入 按唯一键去重；UPDATE 仅补空属性）。
--            · (104) sys:ai:data:read —— whale-agent 只读数据通道
--            · (105) sys:ai:ops:form    —— 智能体平台写操作确认令牌所需权限
--            · (120) sys:im:chat        —— whale-im 全类 @RequirePermission("sys:im:chat")
--            菜单项待前端 whale-im 子应用（8107）落地后再登记，避免指向 404；
--            super_admin 默认拥有全部权限，其余角色由授权界面分配。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 104, 0, 'sys:ai:data:read', '智能体只读数据查询', 'API', '/agent/tools/data-query', 'POST', 104, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 104 OR perm_code = 'sys:ai:data:read');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 105, 0, 'sys:ai:ops:form', '智能体平台写操作确认', 'API', '/agent/ops/**', 'POST', 105, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 105 OR perm_code = 'sys:ai:ops:form');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 120, 0, 'sys:im:chat', 'IM 会话与消息', 'API', '/im/**', 'GET,POST', 120, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 120 OR perm_code = 'sys:im:chat');

-- 既有库中若已以其它 ID 手工登记过同编码权限，NOT EXISTS 幂等插入 会按唯一键跳过；
-- 此处仅补齐为空的展示属性（path / method），不覆盖已有值，可重复执行。
UPDATE sys_permission
SET path = CASE perm_code
               WHEN 'sys:agent:chat' THEN '/agent/chat'
               WHEN 'sys:ai:data:read' THEN '/agent/tools/data-query'
               WHEN 'sys:ai:ops:form' THEN '/agent/ops/**'
               WHEN 'sys:im:chat' THEN '/im/**'
           END
WHERE path IS NULL
  AND perm_code IN ('sys:agent:chat', 'sys:ai:data:read', 'sys:ai:ops:form', 'sys:im:chat');

UPDATE sys_permission
SET method = CASE perm_code
                 WHEN 'sys:agent:chat' THEN 'POST'
                 WHEN 'sys:ai:data:read' THEN 'POST'
                 WHEN 'sys:ai:ops:form' THEN 'POST'
                 WHEN 'sys:im:chat' THEN 'GET,POST'
             END
WHERE method IS NULL
  AND perm_code IN ('sys:agent:chat', 'sys:ai:data:read', 'sys:ai:ops:form', 'sys:im:chat');
