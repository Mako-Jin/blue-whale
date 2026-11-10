-- =====================================================================
-- P3-9c：PDM 产品（whale-pdm）权限点与菜单迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.60-pdm.sql
-- 幂等说明 : NOT EXISTS 幂等插入，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 权限点 211–214（产品 共 4 个）+ 菜单 27。
-- 编号约定 : 203–206 为 ERP；207–210 为 MES；211–214 为 PDM 占用；
--            菜单 25 为 ERP、26 为 MES，PDM 菜单用 27。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；migration-5.65 已固化
--            pdm_manager / pdm_member 及既有租户管理员，无需再手工勾选 211–214。
-- 前置条件 : bluewhale_pdm 库已执行 backend/whale-apps/whale-pdm/src/main/resources/db/init.sql。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 211, 0, 'sys:pdm:product:list', '查询产品', 'API', '/pdm/product/page,/pdm/product/{id}', 'GET', 211, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 211 OR perm_code = 'sys:pdm:product:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 212, 0, 'sys:pdm:product:add', '新增产品', 'API', '/pdm/product', 'POST', 212, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 212 OR perm_code = 'sys:pdm:product:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 213, 0, 'sys:pdm:product:edit', '编辑产品', 'API', '/pdm/product/{id}', 'PUT', 213, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 213 OR perm_code = 'sys:pdm:product:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 214, 0, 'sys:pdm:product:delete', '删除产品', 'API', '/pdm/product/{id}', 'DELETE', 214, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 214 OR perm_code = 'sys:pdm:product:delete');

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 27, 1, 0, '产品管理', '/pdm', 'pdm/index', 'appstore', 27, 'MENU', 'sys:pdm:product:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 27);
