-- =====================================================================
-- P1 S-04f：印章原图导出权限（bluewhale_auth）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.67-contract-seal-download-permission.sql
-- 幂等说明 : 权限与授权均为 INSERT IGNORE，可重复执行；不修改/删除既有数据。
-- 背景     : 印章预览（带水印）沿用 sys:contract:seal:list；原图导出属于敏感操作，
--            独立权限 sys:contract:seal:download，接口 /contract/seal/{id}/download 同时写审计。
-- 授权口径 : contract_manager（合同全量）与 tenant_admin（租户管理员）可导出原图；
--            contract_member / contract_signatory 不授予，避免签署人获取可复用原章。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (233, 0, 'sys:contract:seal:download', '下载印章原图', 'API',
     '/contract/seal/{id}/download', 'GET', 233, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED),
       r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.id = 233
   AND p.deleted = 0
   AND p.status = 1
WHERE r.deleted = 0
  AND r.status = 1
  AND r.role_code IN ('contract_manager', 'tenant_admin');

-- =====================================================================
-- 完成。校验建议：
--   SELECT p.id, p.perm_code FROM sys_permission p WHERE p.id = 233;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id = 233 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：每个平台/业务租户仅 contract_manager 与 tenant_admin 持有
-- =====================================================================
