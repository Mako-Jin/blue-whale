-- =====================================================================
-- P3-9b：MES 生产工单（whale-mes）权限点与菜单迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.59-mes.sql
-- 幂等说明 : INSERT IGNORE，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 权限点 207–210（生产工单 共 4 个）+ 菜单 26。
-- 编号约定 : 172–202 为 PM 占用；203–206 为 ERP 占用；207–210 为 MES 占用；
--            PDM 从 211 起编。菜单 18–24 为 PM、25 为 ERP，MES 菜单用 26。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；migration-5.65 已固化
--            mes_manager / mes_member 及既有租户管理员，无需再手工勾选 207–210。
-- 前置条件 : bluewhale_mes 库已执行 backend/whale-apps/whale-mes/src/main/resources/db/init.sql。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (207, 0, 'sys:mes:workorder:list', '查询生产工单', 'API', '/mes/work-order/page,/mes/work-order/{id}', 'GET', 207, 1),
    (208, 0, 'sys:mes:workorder:add', '新增生产工单', 'API', '/mes/work-order', 'POST', 208, 1),
    (209, 0, 'sys:mes:workorder:edit', '编辑生产工单', 'API', '/mes/work-order/{id}', 'PUT', 209, 1),
    (210, 0, 'sys:mes:workorder:delete', '删除生产工单', 'API', '/mes/work-order/{id}', 'DELETE', 210, 1);

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (26, 1, 0, '工单管理', '/mes', 'mes/index', 'profile', 26, 'MENU', 'sys:mes:workorder:list', 1, 1);
