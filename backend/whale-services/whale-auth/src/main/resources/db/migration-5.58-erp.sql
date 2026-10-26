-- =====================================================================
-- P3-9a：ERP 物料（whale-erp）权限点与菜单迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.58-erp.sql
-- 幂等说明 : INSERT IGNORE，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 权限点 203–206（物料 共 4 个）+ 菜单 25。
-- 编号约定 : 172–202 为 PM 占用；203–206 为 ERP 占用；MES/PDM 从 207 起编；
--            菜单 18–24 为 PM 占用，ERP 菜单用 25。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；super_admin 隐式覆盖，
--            业务角色请在「角色管理」勾选 203–206。
-- 前置条件 : bluewhale_erp 库已执行 backend/whale-apps/whale-erp/src/main/resources/db/init.sql。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (203, 0, 'sys:erp:item:list', '查询物料', 'API', '/erp/item/page,/erp/item/{id}', 'GET', 203, 1),
    (204, 0, 'sys:erp:item:add', '新增物料', 'API', '/erp/item', 'POST', 204, 1),
    (205, 0, 'sys:erp:item:edit', '编辑物料', 'API', '/erp/item/{id}', 'PUT', 205, 1),
    (206, 0, 'sys:erp:item:delete', '删除物料', 'API', '/erp/item/{id}', 'DELETE', 206, 1);

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (25, 1, 0, '物料管理', '/erp', 'erp/index', 'appstore', 25, 'MENU', 'sys:erp:item:list', 1, 1);
