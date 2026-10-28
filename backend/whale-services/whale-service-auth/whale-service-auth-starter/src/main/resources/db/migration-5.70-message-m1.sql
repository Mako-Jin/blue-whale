-- =====================================================================
-- whale-message M1：模板管理与失败重试权限（bluewhale_auth）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.70-message-m1.sql
-- 幂等说明 : 权限与授权均为 INSERT IGNORE，可重复执行；不修改/删除既有数据。
-- 权限编号 : 234 已被 M0 sys:message:record:list 占用，本脚本从 239 起分配。
-- 授权口径 : tenant_admin 获得模板管理与重试权限；super_admin 由系统内置全量权限覆盖。
-- 站内信   : /message/inapp/** 是登录用户自助能力，只依赖网关 X-User-Id，不分配权限码。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (239, 0, 'sys:message:template:list',   '查询消息模板', 'API',
     '/message/template/page,/message/template/{id}', 'GET',    239, 1),
    (240, 0, 'sys:message:template:add',    '新增消息模板', 'API',
     '/message/template',                              'POST',   240, 1),
    (241, 0, 'sys:message:template:edit',   '编辑消息模板', 'API',
     '/message/template/{id}',                         'PUT',    241, 1),
    (242, 0, 'sys:message:template:delete', '删除消息模板', 'API',
     '/message/template/{id}',                         'DELETE', 242, 1),
    (243, 0, 'sys:message:record:retry',    '重试消息投递', 'API',
     '/message/record/{id}/retry',                     'POST',   243, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED),
       r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.id IN (239, 240, 241, 242, 243)
   AND p.deleted = 0
   AND p.status = 1
WHERE r.deleted = 0
  AND r.status = 1
  AND r.role_code IN ('tenant_admin');

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 239 AND 243;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 239 AND 243 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
-- =====================================================================
