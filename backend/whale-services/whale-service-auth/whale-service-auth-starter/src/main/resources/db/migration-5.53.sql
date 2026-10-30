-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.53：档案服务权限点）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_permission 表的运行库
--            （保留存量权限与角色授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这些权限点）。
-- 说明     : 幂等（NOT EXISTS 幂等插入 按唯一键去重）。段位约定：档案预留 140–159。
--            · 140–143 门类 / 规则维护（M1 已落地接口）
--            · 144–150 条目 / 下载 / 销毁 / 同步（M3 已落地接口）
--            super_admin 默认拥有全部权限，其余角色由授权界面分配。
--            M3 起同步登记 admin 档案管理菜单（11 目录 + 12–15 四页）。
-- =====================================================================

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 11, 0, '档案管理', '/archive', NULL, 'folder-open', 3, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 11);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 12, 11, '门类管理', '/archive/category', 'archive/category/index', 'tree', 1, 'MENU', 'sys:archive:category:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 12);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 13, 11, '归档规则', '/archive/rule', 'archive/rule/index', 'setting', 2, 'MENU', 'sys:archive:rule:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 13);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 14, 11, '档案检索', '/archive/item', 'archive/item/index', 'search', 3, 'MENU', 'sys:archive:item:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 14);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 15, 11, '同步状态', '/archive/sync', 'archive/sync/index', 'sync', 4, 'MENU', 'sys:archive:item:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 15);
INSERT INTO sys_menu (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 16, 11, '待整理', '/archive/pending', 'archive/pending/index', 'inbox', 0, 'MENU', 'sys:archive:item:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 16);

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 140, 0, 'sys:archive:category:list', '查询档案门类', 'API', '/archive/category/tree', 'GET', 140, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 140 OR perm_code = 'sys:archive:category:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 141, 0, 'sys:archive:category:manage', '维护档案门类', 'API', '/archive/category/**', 'POST,PUT,DELETE', 141, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 141 OR perm_code = 'sys:archive:category:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 142, 0, 'sys:archive:rule:list', '查询归档规则', 'API', '/archive/rule/page', 'GET', 142, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 142 OR perm_code = 'sys:archive:rule:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 143, 0, 'sys:archive:rule:manage', '维护归档规则', 'API', '/archive/rule/**', 'POST,PUT,DELETE', 143, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 143 OR perm_code = 'sys:archive:rule:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 144, 0, 'sys:archive:item:list', '查询档案条目', 'API', '/archive/item/**', 'GET', 144, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 144 OR perm_code = 'sys:archive:item:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 145, 0, 'sys:archive:item:read', '查看档案详情', 'API', '/archive/item/{id}', 'GET', 145, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 145 OR perm_code = 'sys:archive:item:read');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 146, 0, 'sys:archive:item:edit', '整理档案条目', 'API', '/archive/item/{id}', 'PUT', 146, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 146 OR perm_code = 'sys:archive:item:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 147, 0, 'sys:archive:item:archive', '确认归档', 'API', '/archive/item/{id}/confirm', 'POST', 147, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 147 OR perm_code = 'sys:archive:item:archive');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 148, 0, 'sys:archive:item:download', '下载档案文件', 'API', '/archive/item/{id}/files/{fileId}/download', 'GET', 148, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 148 OR perm_code = 'sys:archive:item:download');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 149, 0, 'sys:archive:item:destroy', '销毁档案', 'API', '/archive/item/{id}/destroy', 'POST', 149, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 149 OR perm_code = 'sys:archive:item:destroy');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 150, 0, 'sys:archive:sync:run', '触发档案同步', 'API', '/archive/sync/run', 'POST', 150, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 150 OR perm_code = 'sys:archive:sync:run');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 151, 0, 'sys:archive:secret:read', '检索秘密级档案知识', 'API', '/agent/search,/agent/chat', 'GET,POST', 151, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 151 OR perm_code = 'sys:archive:secret:read');

-- ---------------------------------------------------------------------
-- Wave 5.53 M4：文件知识库权限点（whale-agent 8090）
-- · 106 查看文件知识库（分页与统计，只读）
-- · 107 管理文件知识库（重建 / 重索引 / 启停）
-- 说明：复用 100–107 段位，不与档案预留段位 140–159 冲突。
-- ---------------------------------------------------------------------

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 106, 0, 'sys:agent:knowledge:list', '查看文件知识库', 'API', '/agent/knowledge/files/page,/agent/knowledge/stats', 'GET', 106, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 106 OR perm_code = 'sys:agent:knowledge:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 107, 0, 'sys:agent:knowledge:manage', '管理文件知识库', 'API', '/agent/knowledge/**', 'POST,PUT', 107, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 107 OR perm_code = 'sys:agent:knowledge:manage');
