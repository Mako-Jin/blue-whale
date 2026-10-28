-- =====================================================================
-- whale-message M0：消息记录查询权限（bluewhale_auth）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.68-message-permission.sql
-- 幂等说明 : 权限与授权均为 INSERT IGNORE，可重复执行；不修改/删除既有数据。
-- 背景     : 统一消息服务 /message/record/** 为运营查询接口，需独立权限
--            sys:message:record:list；/message/internal/** 仅内网调用，不授予权限。
-- 授权口径 : tenant_admin 可查看本租户消息记录；super_admin 由系统内置全量权限覆盖。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (234, 0, 'sys:message:record:list', '查询消息记录', 'API',
     '/message/record/page,/message/record/{id}', 'GET', 234, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED),
       r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.id = 234
   AND p.deleted = 0
   AND p.status = 1
WHERE r.deleted = 0
  AND r.status = 1
  AND r.role_code IN ('tenant_admin');

-- =====================================================================
-- 完成。校验建议：
--   SELECT p.id, p.perm_code FROM sys_permission p WHERE p.id = 234;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id = 234 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
-- =====================================================================
