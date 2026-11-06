-- ============================================================================
-- Wave 5.82 OA M1A-4：请假附件上传权限
-- 执行库 : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m1a4.sql
-- 说明 : 附件上传复用平台 /file/upload（权限 32 sys:file:upload）；
--        OA 角色（含普通员工 oa_member）需要该权限，故按角色幂等授权。
-- ============================================================================

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':32')), 1, 15), 16, 10) AS UNSIGNED) AS id,
       r.id AS role_id,
       p.id AS permission_id
FROM sys_role r
JOIN sys_permission p ON p.id = 32 AND p.deleted = 0 AND p.status = 1
WHERE r.deleted = 0
  AND r.status = 1
  AND r.role_code IN ('oa_admin', 'oa_hr_admin', 'oa_manager', 'oa_member', 'tenant_admin')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
