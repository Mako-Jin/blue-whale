-- =====================================================================
-- Wave 5.61 M1：合同管理（whale-contract）权限点与菜单迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.61-contract.sql
-- 幂等说明 : NOT EXISTS 幂等插入，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 权限点 215–222（合同主流程 8 个）+ 菜单 28。
-- 编号约定 : 203–206 ERP / 207–210 MES / 211–214 PDM 已占用；
--            菜单 25/26/27 已占用，合同菜单用 28。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；migration-5.65 已固化
--            contract_manager / contract_member / contract_signatory 及既有租户管理员，
--            无需再手工勾选 215–222。
-- 前置条件 : bluewhale_contract 库已执行 backend/whale-apps/whale-contract/src/main/resources/db/init.sql。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 215, 0, 'sys:contract:list', '查询合同', 'API', '/contract/page,/contract/{id},/contract/{id}/versions', 'GET', 215, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 215 OR perm_code = 'sys:contract:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 216, 0, 'sys:contract:add', '新建合同', 'API', '/contract', 'POST', 216, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 216 OR perm_code = 'sys:contract:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 217, 0, 'sys:contract:edit', '编辑合同', 'API', '/contract/{id},/contract/{id}/versions', 'PUT,POST', 217, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 217 OR perm_code = 'sys:contract:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 218, 0, 'sys:contract:delete', '删除合同', 'API', '/contract/{id}', 'DELETE', 218, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 218 OR perm_code = 'sys:contract:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 219, 0, 'sys:contract:submit', '提交合同审批', 'API', '/contract/{id}/submit', 'POST', 219, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 219 OR perm_code = 'sys:contract:submit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 220, 0, 'sys:contract:withdraw', '撤回合同审批', 'API', '/contract/{id}/withdraw', 'POST', 220, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 220 OR perm_code = 'sys:contract:withdraw');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 221, 0, 'sys:contract:terminate', '终止合同', 'API', '/contract/{id}/terminate', 'POST', 221, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 221 OR perm_code = 'sys:contract:terminate');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 222, 0, 'sys:contract:archive', '归档合同', 'API', '/contract/{id}/archive', 'POST', 222, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 222 OR perm_code = 'sys:contract:archive');

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 28, 1, 0, '合同管理', '/contract', 'contract/index', 'file-text', 28, 'MENU', 'sys:contract:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 28);
