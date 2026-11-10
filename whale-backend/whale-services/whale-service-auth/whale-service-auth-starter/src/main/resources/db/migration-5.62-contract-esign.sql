-- =====================================================================
-- Wave 5.61 M2：印章与本地盖章权限点与菜单迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.62-contract-esign.sql
-- 幂等说明 : NOT EXISTS 幂等插入，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 权限点 223–227、229–231（印章 + 签署 + 证据）+ 菜单 29、30。
-- 编号约定 : M1 已占用权限 215–222、菜单 28；证据中心菜单 31 留待 M3。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；migration-5.65 已固化
--            contract_manager / contract_signatory 及既有租户管理员，无需再手工勾选 223–231。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 223, 0, 'sys:contract:seal:list', '查询印章', 'API', '/contract/seal/page,/contract/seal/{id}/grants', 'GET', 223, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 223 OR perm_code = 'sys:contract:seal:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 224, 0, 'sys:contract:seal:add', '新建印章', 'API', '/contract/seal,/contract/seal/upload-image', 'POST', 224, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 224 OR perm_code = 'sys:contract:seal:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 225, 0, 'sys:contract:seal:edit', '编辑印章', 'API', '/contract/seal/{id}', 'PUT', 225, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 225 OR perm_code = 'sys:contract:seal:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 226, 0, 'sys:contract:seal:delete', '删除印章', 'API', '/contract/seal/{id}', 'DELETE', 226, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 226 OR perm_code = 'sys:contract:seal:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 227, 0, 'sys:contract:seal:grant', '用章授权', 'API', '/contract/seal/{id}/grant', 'POST', 227, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 227 OR perm_code = 'sys:contract:seal:grant');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 228, 0, 'sys:contract:sign:start', '发起合同签署', 'API', '/contract/{id}/sign/start', 'POST', 228, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 228 OR perm_code = 'sys:contract:sign:start');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 229, 0, 'sys:contract:sign:execute', '执行合同签署', 'API', '/contract/sign/pending,/contract/sign-task/{id}/sign,/contract/sign-task/{id}/refuse', 'GET,POST', 229, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 229 OR perm_code = 'sys:contract:sign:execute');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 230, 0, 'sys:contract:evidence:read', '查询签署证据与验签', 'API', '/contract/{id}/evidence,/contract/{id}/verify', 'GET,POST', 230, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 230 OR perm_code = 'sys:contract:evidence:read');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 231, 0, 'sys:contract:sign:void', '作废合同', 'API', '/contract/{id}/void', 'POST', 231, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 231 OR perm_code = 'sys:contract:sign:void');

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 29, 1, 0, '印章管理', '/contract/seal', 'contract/seal', 'safety-certificate', 29, 'MENU', 'sys:contract:seal:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 29);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 30, 1, 0, '签署中心', '/contract/sign', 'contract/sign', 'edit', 30, 'MENU', 'sys:contract:sign:execute', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 30);
