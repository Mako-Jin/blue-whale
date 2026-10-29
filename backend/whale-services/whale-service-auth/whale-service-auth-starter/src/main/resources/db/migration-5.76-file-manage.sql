-- =====================================================================
-- Wave 5.53 补齐：文件归属隔离与全租户管理权限（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.76-file-manage.sql
-- 幂等说明 : 权限与授权均为 INSERT IGNORE，可重复执行；不修改/删除既有数据。
-- 背景     : 文件服务默认仅返回本人上传的文件；持有 sys:file:manage 者可查看、
--            下载、删除全租户文件。内网 /file/internal/** 通道不受影响。
-- 权限编号 : 251–279 已被文档 / 配置服务占用，本脚本使用 280。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（280）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (280, 0, 'sys:file:manage', '管理全租户文件', 'API',
     '/file/page,/file/{id}/download,/file/{id}/preview,/file/{id}/presigned,/file/{id}',
     'GET,DELETE', 280, 1);

-- ---------------------------------------------------------------------
-- 2. 授权：tenant_admin 获得 280（super_admin 由 /auth/me 返回全量权限码）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED),
       r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.id = 280
   AND p.deleted = 0
   AND p.status = 1
WHERE r.deleted = 0
  AND r.status = 1
  AND r.role_code IN ('tenant_admin');

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id = 280;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id = 280 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
-- =====================================================================
