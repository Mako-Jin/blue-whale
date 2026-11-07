-- =====================================================================
-- Wave 5.82 M5-7d-1：OA 企业文化 / 投票问卷 / 建议箱 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m5-culture.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增文化动态、生日/周年祝福、投票问卷与建议箱。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1780–1787。
-- 菜单编号 : OA 段 360–409；本脚本使用 395–397。
-- 授权口径 : oa_admin、tenant_admin 获全量 1780–1787；
--            oa_manager 获全量（含投票与提建议）；
--            oa_member  获 1780/1782/1784/1785/1786（只读 + 投票 + 提建议）。
-- 前置条件 : whale-oa V13__culture.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1780–1787
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1780, 0, 'sys:oa:culture:list', '查看企业文化', 'API',
       '/oa/culture/events/page,/oa/culture/events/{id},/oa/culture/birthdays,/oa/culture/wishes/page',
       'GET', 1780, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1780 OR perm_code = 'sys:oa:culture:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1781, 0, 'sys:oa:culture:add', '维护文化动态', 'API',
       '/oa/culture/events,/oa/culture/events/{id},/oa/culture/events/{id}/publish',
       'ALL', 1781, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1781 OR perm_code = 'sys:oa:culture:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1782, 0, 'sys:oa:poll:list', '查看投票问卷', 'API',
       '/oa/poll/page,/oa/poll/{id}', 'GET', 1782, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1782 OR perm_code = 'sys:oa:poll:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1783, 0, 'sys:oa:poll:add', '维护投票问卷', 'API',
       '/oa/poll,/oa/poll/{id},/oa/poll/{id}/publish,/oa/poll/{id}/close', 'ALL', 1783, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1783 OR perm_code = 'sys:oa:poll:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1784, 0, 'sys:oa:poll:vote', '参与投票', 'API',
       '/oa/poll/{id}/vote', 'POST', 1784, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1784 OR perm_code = 'sys:oa:poll:vote');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1785, 0, 'sys:oa:suggestion:list', '查看建议箱', 'API',
       '/oa/suggestion/page,/oa/suggestion/{id}', 'GET', 1785, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1785 OR perm_code = 'sys:oa:suggestion:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1786, 0, 'sys:oa:suggestion:add', '提交建议', 'API',
       '/oa/suggestion', 'POST', 1786, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1786 OR perm_code = 'sys:oa:suggestion:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1787, 0, 'sys:oa:suggestion:reply', '回复/关闭建议', 'API',
       '/oa/suggestion/{id}/reply,/oa/suggestion/{id}/close', 'ALL', 1787, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1787 OR perm_code = 'sys:oa:suggestion:reply');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色分段）
-- ---------------------------------------------------------------------
-- 2.1 全量：oa_admin、tenant_admin
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'tenant_admin') AND p.id BETWEEN 1780 AND 1787) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- 2.2 审批管理：oa_manager
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'oa_manager' AND p.id BETWEEN 1780 AND 1787) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- 2.3 普通员工：oa_member
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'oa_member' AND p.id IN (1780, 1782, 1784, 1785, 1786)) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 395–397
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 395, 1, 0, '企业文化', '/oa/culture', 'culture/index', 'smile', 395, 'MENU', 'sys:oa:culture:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 395);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 396, 1, 0, '投票问卷', '/oa/poll', 'poll/index', 'bar-chart', 396, 'MENU', 'sys:oa:poll:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 396);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 397, 1, 0, '建议箱', '/oa/suggestion', 'suggestion/index', 'message', 397, 'MENU', 'sys:oa:suggestion:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 397);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id BETWEEN 1780 AND 1787 ORDER BY id;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id IN (395, 396, 397) ORDER BY id;
-- =====================================================================
