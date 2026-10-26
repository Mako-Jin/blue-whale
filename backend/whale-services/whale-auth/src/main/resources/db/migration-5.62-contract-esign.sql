-- =====================================================================
-- Wave 5.61 M2：印章与本地盖章权限点与菜单迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.62-contract-esign.sql
-- 幂等说明 : INSERT IGNORE，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 权限点 223–227、229–231（印章 + 签署 + 证据）+ 菜单 29、30。
-- 编号约定 : M1 已占用权限 215–222、菜单 28；证据中心菜单 31 留待 M3。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；super_admin 隐式覆盖，
--            业务角色请在「角色管理」勾选 223–231。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (223, 0, 'sys:contract:seal:list', '查询印章', 'API',
     '/contract/seal/page,/contract/seal/{id}/grants', 'GET', 223, 1),
    (224, 0, 'sys:contract:seal:add', '新建印章', 'API',
     '/contract/seal,/contract/seal/upload-image', 'POST', 224, 1),
    (225, 0, 'sys:contract:seal:edit', '编辑印章', 'API', '/contract/seal/{id}', 'PUT', 225, 1),
    (226, 0, 'sys:contract:seal:delete', '删除印章', 'API', '/contract/seal/{id}', 'DELETE', 226, 1),
    (227, 0, 'sys:contract:seal:grant', '用章授权', 'API', '/contract/seal/{id}/grant', 'POST', 227, 1),
    (228, 0, 'sys:contract:sign:start', '发起合同签署', 'API', '/contract/{id}/sign/start', 'POST', 228, 1),
    (229, 0, 'sys:contract:sign:execute', '执行合同签署', 'API',
     '/contract/sign/pending,/contract/sign-task/{id}/sign,/contract/sign-task/{id}/refuse', 'GET,POST', 229, 1),
    (230, 0, 'sys:contract:evidence:read', '查询签署证据与验签', 'API',
     '/contract/{id}/evidence,/contract/{id}/verify', 'GET,POST', 230, 1),
    (231, 0, 'sys:contract:sign:void', '作废合同', 'API', '/contract/{id}/void', 'POST', 231, 1);

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (29, 1, 0, '印章管理', '/contract/seal', 'contract/seal', 'safety-certificate', 29, 'MENU',
     'sys:contract:seal:list', 1, 1),
    (30, 1, 0, '签署中心', '/contract/sign', 'contract/sign', 'edit', 30, 'MENU',
     'sys:contract:sign:execute', 1, 1);
