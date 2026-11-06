-- =====================================================================
-- Wave 5.89 M1 补登：收信规则权限（whale-mail）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.90-mail-rule.sql
-- 幂等说明 : 权限与授权均 NOT EXISTS 插入；两条 UPDATE 是「把新增接口路径补登到已有权限点」，
--            重复执行不会产生副作用（值固定，不是累加）。
-- 权限编号 : 2109（2100–2108 已在 migration-5.89-mail 中占用）
-- 背景     : 规则是本轮新增能力，但 M1 里已经有三个接口借用已有权限码却没登记路径——
--            /mail/rule/list、/mail/search/status、/mail/delivery/report/page、
--            /mail/folder/refresh。这里一并补登，避免审计时「接口存在但权限清单里找不到」。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 新增权限点：规则管理（2109）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2109, 0, 'sys:mail:rule:manage', '管理收信规则', 'API',
       '/mail/rule/save,/mail/rule/enabled,/mail/rule/remove,/mail/rule/apply', 'POST', 2109, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2109 OR perm_code = 'sys:mail:rule:manage');

-- ---------------------------------------------------------------------
-- 2. 补登 M1 新增接口到已有权限点的路径清单
-- ---------------------------------------------------------------------
-- 查看类：规则列表、检索状态、退信回执分页
UPDATE sys_permission
SET path = '/mail/message/page,/mail/message/{id},/mail/message/unread,/mail/folder/list,/mail/draft/page,/mail/rule/list,/mail/search/status,/mail/delivery/report/page'
WHERE perm_code = 'sys:mail:message:list' AND deleted = 0;

-- 同步类：文件夹探测刷新（手动刷新与「同步邮件」同属一次 IMAP 往返）
UPDATE sys_permission
SET path = '/mail/sync/{accountId},/mail/folder/refresh,/mail/search/reindex'
WHERE perm_code = 'sys:mail:sync' AND deleted = 0;

-- ---------------------------------------------------------------------
-- 3. 租户管理员授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (2109) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id = 2109 OR perm_code IN ('sys:mail:message:list', 'sys:mail:sync');
--   SELECT r.tenant_id, COUNT(*) AS granted FROM sys_role r
--     JOIN sys_role_permission rp ON rp.role_id = r.id
--     WHERE rp.permission_id = 2109 AND r.deleted = 0 GROUP BY r.tenant_id;
-- =====================================================================
