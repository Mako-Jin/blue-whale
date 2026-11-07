-- =====================================================================
-- Wave 5.82 M3-P1：OA 协同办公 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m3-collab.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增会议室、会议、参会人、纪要、行动项、公告、
--            公告接收人、个人任务与任务评论。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1730–1739。
-- 菜单编号 : OA 段 360–409；本脚本使用 383–385。
-- 授权口径 : oa_admin、oa_manager、tenant_admin 获得 1730–1739 全量；
--            oa_member 仅获得 1730/1732/1734/1737/1739（浏览与评论）。
-- 前置条件 : bluewhale_oa 已执行 V9__collab.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1730–1739
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1730, 0, 'sys:oa:meeting:room:list', '查询会议室', 'API',
       '/oa/meeting/rooms/page,/oa/meeting/rooms/all', 'GET', 1730, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1730 OR perm_code = 'sys:oa:meeting:room:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1731, 0, 'sys:oa:meeting:room:manage', '维护会议室', 'API',
       '/oa/meeting/rooms,/oa/meeting/rooms/{id}', 'POST,PUT,DELETE', 1731, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1731 OR perm_code = 'sys:oa:meeting:room:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1732, 0, 'sys:oa:meeting:list', '查询会议', 'API',
       '/oa/meeting/page,/oa/meeting/{id}', 'GET', 1732, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1732 OR perm_code = 'sys:oa:meeting:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1733, 0, 'sys:oa:meeting:manage', '管理会议与纪要', 'API',
       '/oa/meeting,/oa/meeting/{id},/oa/meeting/{id}/status,/oa/meeting/{id}/participants/{userId}/status,/oa/meeting/{id}/minute',
       'POST,PUT', 1733, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1733 OR perm_code = 'sys:oa:meeting:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1734, 0, 'sys:oa:notice:list', '查看公告', 'API',
       '/oa/notice/page,/oa/notice/my/page,/oa/notice/{id},/oa/notice/{id}/read', 'GET,PUT', 1734, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1734 OR perm_code = 'sys:oa:notice:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1735, 0, 'sys:oa:notice:manage', '发布公告', 'API',
       '/oa/notice,/oa/notice/{id},/oa/notice/{id}/publish,/oa/notice/{id}/withdraw,/oa/notice/recipients',
       'GET,POST,PUT', 1735, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1735 OR perm_code = 'sys:oa:notice:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1736, 0, 'sys:oa:notice:stats', '公告已读统计', 'API', '/oa/notice/{id}/stats', 'GET', 1736, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1736 OR perm_code = 'sys:oa:notice:stats');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1737, 0, 'sys:oa:task:list', '查询任务与评论', 'API',
       '/oa/task/page,/oa/task/{id},/oa/task/{id}/comments', 'GET', 1737, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1737 OR perm_code = 'sys:oa:task:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1738, 0, 'sys:oa:task:manage', '管理任务', 'API',
       '/oa/task,/oa/task/{id},/oa/task/{id}/status', 'POST,PUT,DELETE', 1738, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1738 OR perm_code = 'sys:oa:task:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1739, 0, 'sys:oa:task:comment', '任务评论', 'API',
       '/oa/task/{id}/comments,/oa/task/{id}/comments/{commentId}', 'POST,DELETE', 1739, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1739 OR perm_code = 'sys:oa:task:comment');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_manager', 'tenant_admin') AND p.id BETWEEN 1730 AND 1739) OR (r.role_code = 'oa_member' AND p.id IN (1730, 1732, 1734, 1737, 1739)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 383–385
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 383, 1, 0, '会议管理', '/oa/collab/meeting', 'collab/meeting/index', 'team', 383, 'MENU', 'sys:oa:meeting:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 383);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 384, 1, 0, '公告管理', '/oa/collab/notice', 'collab/notice/index', 'notification', 384, 'MENU', 'sys:oa:notice:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 384);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 385, 1, 0, '我的任务', '/oa/collab/task', 'collab/task/index', 'check-square', 385, 'MENU', 'sys:oa:task:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 385);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1730 AND 1739 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 383 AND 385;
-- =====================================================================
