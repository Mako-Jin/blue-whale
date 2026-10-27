-- =====================================================================
-- Wave 5.46 轨 G：OA 请假闭环权限点迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.46-oa-leave.sql
-- 幂等说明 : INSERT IGNORE，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 新增 OA 请假权限点 168–171（对应 OaLeaveController 的 6 个端点）。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；migration-5.65 已固化
--            oa_manager / oa_member 及既有租户管理员，无需再手工勾选 168–171。
-- 前置条件 : bluewhale_oa 库已执行 backend/whale-apps/whale-oa/src/main/resources/db/init.sql。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (168, 0, 'sys:oa:leave:list', '查询请假申请', 'API', '/oa/leave/page,/oa/leave/{id}', 'GET', 168, 1),
    (169, 0, 'sys:oa:leave:add', '新增请假申请', 'API', '/oa/leave', 'POST', 169, 1),
    (170, 0, 'sys:oa:leave:edit', '编辑/审批请假申请', 'API',
     '/oa/leave/{id},/oa/leave/{id}/status', 'PUT', 170, 1),
    (171, 0, 'sys:oa:leave:delete', '删除请假申请', 'API', '/oa/leave/{id}', 'DELETE', 171, 1);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 168 AND 171;
--   mysql -u<user> -p bluewhale_oa -e "SELECT id, code, status FROM oa_leave;"
-- =====================================================================
