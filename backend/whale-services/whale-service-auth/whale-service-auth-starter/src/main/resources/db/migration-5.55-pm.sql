-- =====================================================================
-- Wave 5.55 M1 + M2 + M3 + M4 + M5：项目管理（whale-pm）权限点与菜单迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.55-pm.sql
-- 幂等说明 : NOT EXISTS 幂等插入，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 权限点 172–186（项目 / 成员 / 状态 / 流转 共 15 个）
--            + 权限点 187–196（工作项 / 评论 / 附件 / 关联 共 10 个）+ 菜单 18–21
--            + 权限点 197–200（迭代 / 看板 共 4 个）+ 菜单 22–23
--            + 权限点 201（报表查看）+ 菜单 24
--            + 权限点 202（发起工作项审批）。
-- 编号约定 : 172–186 为 M1 占用；187–196 为 M2 占用；197–200 为 M3 占用；
--            201 为 M4 占用；202 为 M5 占用；M6+ 从 203 起编。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；上线后请在
--            「角色管理」为租户管理员 / 业务角色勾选 172–202。
-- 前置条件 : bluewhale_pm 库已执行 backend/whale-apps/whale-pm/src/main/resources/db/init.sql。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 172, 0, 'sys:pm:project:list', '查询项目', 'API', '/pm/project/page,/pm/project/{id}', 'GET', 172, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 172 OR perm_code = 'sys:pm:project:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 173, 0, 'sys:pm:project:add', '新增项目', 'API', '/pm/project', 'POST', 173, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 173 OR perm_code = 'sys:pm:project:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 174, 0, 'sys:pm:project:edit', '编辑/归档项目', 'API', '/pm/project/{id},/pm/project/{id}/status', 'PUT', 174, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 174 OR perm_code = 'sys:pm:project:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 175, 0, 'sys:pm:project:delete', '删除项目', 'API', '/pm/project/{id}', 'DELETE', 175, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 175 OR perm_code = 'sys:pm:project:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 176, 0, 'sys:pm:member:list', '查询项目成员', 'API', '/pm/project/{id}/member', 'GET', 176, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 176 OR perm_code = 'sys:pm:member:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 177, 0, 'sys:pm:member:add', '新增项目成员', 'API', '/pm/project/{id}/member', 'POST', 177, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 177 OR perm_code = 'sys:pm:member:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 178, 0, 'sys:pm:member:edit', '变更成员角色', 'API', '/pm/member/{id}', 'PUT', 178, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 178 OR perm_code = 'sys:pm:member:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 179, 0, 'sys:pm:member:delete', '移除项目成员', 'API', '/pm/member/{id}', 'DELETE', 179, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 179 OR perm_code = 'sys:pm:member:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 180, 0, 'sys:pm:status:list', '查询状态配置', 'API', '/pm/project/{id}/status', 'GET', 180, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 180 OR perm_code = 'sys:pm:status:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 181, 0, 'sys:pm:status:add', '新增状态', 'API', '/pm/project/{id}/status', 'POST', 181, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 181 OR perm_code = 'sys:pm:status:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 182, 0, 'sys:pm:status:edit', '更新状态', 'API', '/pm/status/{id}', 'PUT', 182, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 182 OR perm_code = 'sys:pm:status:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 183, 0, 'sys:pm:status:delete', '删除状态', 'API', '/pm/status/{id}', 'DELETE', 183, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 183 OR perm_code = 'sys:pm:status:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 184, 0, 'sys:pm:transition:list', '查询流转配置', 'API', '/pm/project/{id}/transition', 'GET', 184, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 184 OR perm_code = 'sys:pm:transition:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 185, 0, 'sys:pm:transition:add', '新增状态流转', 'API', '/pm/project/{id}/transition', 'POST', 185, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 185 OR perm_code = 'sys:pm:transition:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 186, 0, 'sys:pm:transition:delete', '删除状态流转', 'API', '/pm/transition/{id}', 'DELETE', 186, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 186 OR perm_code = 'sys:pm:transition:delete');

-- M2 工作项域：工作项 / 评论 / 附件 / 关联
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 187, 0, 'sys:pm:item:list', '查询工作项与动态', 'API', '/pm/work-item/page,/pm/work-item/{id},/pm/work-item/{id}/activity,/pm/project/{projectId}/activity,'
     '/pm/work-item/{id}/comment,/pm/work-item/{id}/attachment,/pm/work-item/{id}/link', 'GET', 187, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 187 OR perm_code = 'sys:pm:item:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 188, 0, 'sys:pm:item:add', '新增工作项', 'API', '/pm/work-item', 'POST', 188, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 188 OR perm_code = 'sys:pm:item:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 189, 0, 'sys:pm:item:edit', '编辑工作项', 'API', '/pm/work-item/{id}', 'PUT', 189, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 189 OR perm_code = 'sys:pm:item:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 190, 0, 'sys:pm:item:delete', '删除工作项', 'API', '/pm/work-item/{id}', 'DELETE', 190, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 190 OR perm_code = 'sys:pm:item:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 191, 0, 'sys:pm:item:transition', '工作项状态流转', 'API', '/pm/work-item/{id}/transition', 'PUT', 191, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 191 OR perm_code = 'sys:pm:item:transition');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 192, 0, 'sys:pm:item:rank', '调整工作项排序', 'API', '/pm/work-item/{id}/rank', 'PUT', 192, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 192 OR perm_code = 'sys:pm:item:rank');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 193, 0, 'sys:pm:comment:add', '新增工作项评论', 'API', '/pm/work-item/{id}/comment', 'POST', 193, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 193 OR perm_code = 'sys:pm:comment:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 194, 0, 'sys:pm:comment:delete', '删除工作项评论', 'API', '/pm/comment/{id}', 'DELETE', 194, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 194 OR perm_code = 'sys:pm:comment:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 195, 0, 'sys:pm:attachment:edit', '登记/解除工作项附件', 'API', '/pm/work-item/{id}/attachment,/pm/attachment/{id}', 'POST,DELETE', 195, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 195 OR perm_code = 'sys:pm:attachment:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 196, 0, 'sys:pm:link:edit', '新增/删除工作项关联', 'API', '/pm/work-item/{id}/link,/pm/work-item/link/{id}', 'POST,DELETE', 196, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 196 OR perm_code = 'sys:pm:link:edit');

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 18, 0, '项目管理', '/pm', NULL, 'project', 4, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 18);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 19, 18, '项目列表', '/pm/project', 'pm/project/index', 'list', 1, 'MENU', 'sys:pm:project:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 19);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 20, 18, '状态配置', '/pm/settings/status', 'pm/settings/status/index', 'setting', 2, 'MENU', 'sys:pm:status:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 20);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 21, 18, '流转配置', '/pm/settings/transition', 'pm/settings/transition/index', 'swap', 3, 'MENU', 'sys:pm:transition:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 21);

-- =====================================================================
-- Wave 5.55 M3：迭代与看板（权限点 197–200 + 菜单 22–23）
-- =====================================================================
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 197, 0, 'sys:pm:sprint:list', '查询迭代与迭代工作项', 'API', '/pm/sprint/page,/pm/sprint/{id},/pm/sprint/{id}/item', 'GET', 197, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 197 OR perm_code = 'sys:pm:sprint:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 198, 0, 'sys:pm:sprint:manage', '维护迭代与迭代工作项', 'API', '/pm/sprint,/pm/sprint/{id},/pm/sprint/{id}/start,/pm/sprint/{id}/close,'
     '/pm/sprint/{id}/item,/pm/sprint/{id}/item/{itemId}', 'POST,PUT,DELETE', 198, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 198 OR perm_code = 'sys:pm:sprint:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 199, 0, 'sys:pm:board:view', '查询看板与执行视图', 'API', '/pm/board,/pm/board/{id},/pm/board/{id}/view', 'GET', 199, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 199 OR perm_code = 'sys:pm:board:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 200, 0, 'sys:pm:board:manage', '维护看板与看板列', 'API', '/pm/board,/pm/board/{id},/pm/board/{id}/column,/pm/board/{id}/column/{columnId}', 'POST,PUT,DELETE', 200, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 200 OR perm_code = 'sys:pm:board:manage');

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 22, 18, '迭代管理', '/pm/sprint', 'pm/sprint/index', 'calendar', 4, 'MENU', 'sys:pm:sprint:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 22);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 23, 18, '看板', '/pm/board', 'pm/board/index', 'kanban', 5, 'MENU', 'sys:pm:board:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 23);

-- =====================================================================
-- Wave 5.55 M4：报表（权限点 201 + 菜单 24）
-- =====================================================================
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 201, 0, 'sys:pm:report:view', '查看项目报表', 'API', '/pm/report/burndown,/pm/report/velocity,/pm/report/cumulative-flow,/pm/report/workload', 'GET', 201, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 201 OR perm_code = 'sys:pm:report:view');

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 24, 18, '项目报表', '/pm/report', 'pm/report/index', 'chart', 6, 'MENU', 'sys:pm:report:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 24);

-- =====================================================================
-- Wave 5.55 M5：集成（权限点 202；无新增菜单）
-- =====================================================================
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 202, 0, 'sys:pm:item:approval', '发起工作项审批', 'API', '/pm/work-item/{id}/approval', 'POST', 202, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 202 OR perm_code = 'sys:pm:item:approval');

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 172 AND 202;
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 18 AND 24;
-- =====================================================================
