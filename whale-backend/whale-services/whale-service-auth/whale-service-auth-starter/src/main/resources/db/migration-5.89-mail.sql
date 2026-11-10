-- =====================================================================
-- Wave 5.89 M0：邮箱服务（whale-mail）权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.89-mail.sql
-- 幂等说明 : 权限、授权、菜单均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 边界说明 : 本脚本只登记「邮箱域」权限。消息管控（/message/**）权限已在
--            migration-5.70 / 5.76 中登记，两者不共用权限码。
-- 权限编号 : 2100–2108（2100 前已用到 2002，2100 段经全量扫描确认空闲）。
-- 菜单编号 : 490（470–489 已登记为党政工会段〔470 工作台 / 471–476 党建 / 481–485 工会基层五页 / 486–489 预留给 M-P4〕，
--            邮件不属于该段，改用其后第一个空闲号 490）。
-- 授权口径 : tenant_admin 获得全部 9 项；super_admin 由系统内置全量覆盖。
--            普通用户的邮箱自助权限建议通过「邮箱用户」角色单独授予
--            （2104/2105/2106/2107/2108），避免把账号绑定权限下放到全员。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点（2100–2108）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2100, 0, 'sys:mail:account:list', '查看邮箱账号', 'API', '/mail/account/current,/mail/account/list', 'GET', 2100, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2100 OR perm_code = 'sys:mail:account:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2101, 0, 'sys:mail:account:add', '绑定邮箱账号', 'API', '/mail/account', 'POST', 2101, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2101 OR perm_code = 'sys:mail:account:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2102, 0, 'sys:mail:account:edit', '编辑邮箱账号', 'API', '/mail/account/{id}', 'PUT', 2102, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2102 OR perm_code = 'sys:mail:account:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2103, 0, 'sys:mail:account:remove', '解绑邮箱账号', 'API', '/mail/account/{id}', 'DELETE', 2103, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2103 OR perm_code = 'sys:mail:account:remove');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2104, 0, 'sys:mail:message:list', '查看邮件', 'API', '/mail/message/page,/mail/message/{id},/mail/message/unread,/mail/folder/list,/mail/draft/page', 'GET', 2104, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2104 OR perm_code = 'sys:mail:message:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2105, 0, 'sys:mail:message:edit', '标记邮件状态', 'API', '/mail/message/{id}/seen,/mail/message/{id}/flagged', 'PUT', 2105, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2105 OR perm_code = 'sys:mail:message:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2106, 0, 'sys:mail:message:send', '发送邮件与保存草稿', 'API', '/mail/send,/mail/draft,/mail/draft/{id}', 'POST', 2106, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2106 OR perm_code = 'sys:mail:message:send');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2107, 0, 'sys:mail:message:remove', '删除邮件', 'API', '/mail/message/{id},/mail/draft/{id}', 'DELETE', 2107, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2107 OR perm_code = 'sys:mail:message:remove');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2108, 0, 'sys:mail:sync', '手动同步邮箱', 'API', '/mail/sync/{accountId}', 'POST', 2108, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2108 OR perm_code = 'sys:mail:sync');

-- ---------------------------------------------------------------------
-- 2. 租户管理员授权（role_code = tenant_admin，按租户逐一授权）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (2100, 2101, 2102, 2103, 2104, 2105, 2106, 2107, 2108) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 管理后台菜单入口（可见性由 permission_code 决定）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 490, 1, 0, '邮箱', '/mail', 'mail/index', 'mail', 490, 'MENU', 'sys:mail:message:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 490);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 2100 AND 2108;
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 490;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 2100 AND 2108 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 以 admin 登录后 GET /auth/me，确认 menus 树包含「邮箱」
-- =====================================================================
