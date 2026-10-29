-- =====================================================================
-- Wave 5.75 M2 剩余：页面级权限 / 标签管理权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.75-doc-m2b.sql
-- 幂等说明 : 权限与授权均为 INSERT IGNORE，可重复执行。
-- 权限编号 : 251–270 为 M1，271–274 为 M3，275 预留审批人、276 预留文档 AI，
--            277 为文档审计，本脚本使用 278–279。
-- 授权口径 : 278（页面级权限）仅授予 doc_manager / tenant_admin；
--            279（标签管理）授予 doc_manager / doc_member / tenant_admin。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（278–279）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (278, 0, 'sys:doc:page:acl',  '管理页面级权限', 'API',
     '/doc/page/{id}/acl',                                  'GET,PUT', 278, 1),
    (279, 0, 'sys:doc:tag:manage','管理文档标签',   'API',
     '/doc/space/{id}/tag,/doc/tag/{id},/doc/page/{id}/tag', 'POST,PUT,DELETE', 279, 1);

-- ---------------------------------------------------------------------
-- 2. 授权
--    278：doc_manager / tenant_admin（空间 OWNER/ADMIN 仍需二次校验）
--    279：doc_manager / doc_member / tenant_admin
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED),
       r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.id IN (278, 279) AND p.deleted = 0 AND p.status = 1
WHERE r.deleted = 0
  AND r.status = 1
  AND (
        (p.id = 278 AND r.role_code IN ('doc_manager', 'tenant_admin'))
     OR (p.id = 279 AND r.role_code IN ('doc_manager', 'doc_member', 'tenant_admin'))
      );

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 278 AND 279;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 278 AND 279 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
-- =====================================================================
