-- =====================================================================
-- 通知任务重试独立权限（bluewhale_auth）
-- ---------------------------------------------------------------------
-- 背景：POST /pay/notify/{id}/retry 原先复用 sys:pay:refund，
--       但「重试通知任务」是运维动作，与「发起退款」职责不同。
-- 变更：新增 sys:pay:notify:retry（id=2423，避开 sys:config:list 占用的 246），并授予既有 tenant_admin；
--       super_admin 由系统内置全量权限覆盖。
-- 幂等：权限与授权均为 NOT EXISTS 插入，可重复执行。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2423, 0, 'sys:pay:notify:retry', '支付通知重试', 'API', '/pay/notify/{id}/retry', 'POST', 2423, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2423 OR perm_code = 'sys:pay:notify:retry');

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
       r.id AS role_id, p.id AS permission_id
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'sys:pay:notify:retry' AND p.deleted = 0 AND p.status = 1
WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission tgt
      WHERE tgt.id = CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED)
         OR (tgt.role_id = r.id AND tgt.permission_id = p.id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE perm_code = 'sys:pay:notify:retry';
-- =====================================================================
