-- =====================================================================
-- Wave 6.43：员工论坛（whale-forum / 鲸议台）权限、角色与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p --default-character-set=utf8mb4 bluewhale_auth < migration-6.43-forum.sql
-- 幂等说明 : 权限、角色、授权、菜单均为 NOT EXISTS 幂等插入；不修改/删除既有数据。
-- 边界说明 : 本脚本只登记「论坛域」权限（sys:forum:*）。文件（/file/**）、消息
--            （/message/**）、商城积分（/mall/**）权限在各自迁移中登记，论坛不重复定义。
-- 权限编号 : 2340–2350（共 11 个）。**2330–2339 属 whale-assistant**
--            （V8__assistant_permission_menu.sql 已应用：2330 define / 2331 chat），
--            原设计「2330–2359」与该段冲突，本脚本已整体后移至 2340–2350。
--            23xx 段其余占用：2300–2306 CRM 分析、2313 twin、2320–2323 PM。
-- 菜单编号 : 510–519（sys_menu 现最大 505，510 起连续，506–509 留给在途批次）。
-- 授权口径 : 按 perm_code 授权，不按 ID 区间——同名权限码在存量库可能挂着另一个 ID，
--            按区间授权必然漏授且不报错（2026-11-09 撞号事故教训）。
--            版主 forum_moderator 只授「本版治理」，不含版块配置与热榜手工刷新。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点（2340–2350）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2340, 0, 'sys:forum:browse', '浏览论坛（版块/帖子/详情）', 'API', '/forum/meta,/forum/board/page,/forum/thread/page,/forum/thread/{id}', 'GET', 2340, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2340 OR perm_code = 'sys:forum:browse');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2341, 0, 'sys:forum:thread:add', '发帖', 'API', '/forum/thread', 'POST', 2341, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2341 OR perm_code = 'sys:forum:thread:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2342, 0, 'sys:forum:thread:edit', '编辑本人帖（服务端仍校验所有权）', 'API', '/forum/thread/{id}', 'PUT', 2342, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2342 OR perm_code = 'sys:forum:thread:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2343, 0, 'sys:forum:thread:remove', '删除本人帖', 'API', '/forum/thread/{id}', 'DELETE', 2343, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2343 OR perm_code = 'sys:forum:thread:remove');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2344, 0, 'sys:forum:post:add', '回帖', 'API', '/forum/thread/{id}/post', 'POST', 2344, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2344 OR perm_code = 'sys:forum:post:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2345, 0, 'sys:forum:reaction:use', '投花/藏帖', 'API', '/forum/reaction/thread/{id}', 'PUT', 2345, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2345 OR perm_code = 'sys:forum:reaction:use');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2346, 0, 'sys:forum:manage:list', '论坛管理端入口（待审/举报/敏感词）', 'API', '/forum/manage/thread/pending/page,/forum/manage/report/page,/forum/manage/sensitive/page', 'GET', 2346, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2346 OR perm_code = 'sys:forum:manage:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2347, 0, 'sys:forum:manage:audit', '审核（通过/驳回，意见必填）', 'API', '/forum/manage/thread/{id}/audit', 'POST', 2347, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2347 OR perm_code = 'sys:forum:manage:audit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2348, 0, 'sys:forum:manage:dispose', '悬榜/题金榜/关闭/处置', 'API', '/forum/manage/thread/{id}/pin,/forum/manage/thread/{id}/essence,/forum/manage/thread/{id}/lock,/forum/manage/thread/{id}/remove,/forum/manage/report/{id}/dispose', 'PUT', 2348, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2348 OR perm_code = 'sys:forum:manage:dispose');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2349, 0, 'sys:forum:manage:board', '版块管理（含审核开关/可见性）', 'API', '/forum/board', 'POST', 2349, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2349 OR perm_code = 'sys:forum:manage:board');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2350, 0, 'sys:forum:hot:refresh', '热榜手工刷新', 'API', '/forum/hot/refresh', 'POST', 2350, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2350 OR perm_code = 'sys:forum:hot:refresh');

-- ---------------------------------------------------------------------
-- 2. 角色：论坛版主（forum_moderator，25139）
--    「部门管理员」不新造角色，用数据范围 (DEPT) 表达；此处只造版主。
--    代码引用的角色码必须有迁移播种，否则授权 JOIN 为空、权限判定静默失效。
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25139, 1, 'forum_moderator', '论坛版主', '本版块内容审核、置顶加精、举报处置与禁言', 259, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25139 OR (tenant_id = 1 AND role_code = 'forum_moderator'));

INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':forum_moderator')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'forum_moderator' AS role_code, '论坛版主' AS role_name, '本版块内容审核、置顶加精、举报处置与禁言' AS description, 259 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 3. 授权：按 perm_code（tenant_admin 全集 + forum_moderator 治理集）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN (
    'sys:forum:browse', 'sys:forum:thread:add', 'sys:forum:thread:edit', 'sys:forum:thread:remove',
    'sys:forum:post:add', 'sys:forum:reaction:use',
    'sys:forum:manage:list', 'sys:forum:manage:audit', 'sys:forum:manage:dispose',
    'sys:forum:manage:board', 'sys:forum:hot:refresh')
  AND p.deleted = 0 AND p.status = 1
WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.role_id = r.id AND tgt.permission_id = p.id);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
FROM sys_role r
JOIN sys_permission p ON p.perm_code IN (
    'sys:forum:browse', 'sys:forum:thread:add', 'sys:forum:thread:edit', 'sys:forum:thread:remove',
    'sys:forum:post:add', 'sys:forum:reaction:use',
    'sys:forum:manage:list', 'sys:forum:manage:audit', 'sys:forum:manage:dispose')
  AND p.deleted = 0 AND p.status = 1
WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('forum_moderator')
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.role_id = r.id AND tgt.permission_id = p.id);

-- ---------------------------------------------------------------------
-- 4. 菜单（510–512）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 510, 1, 0, '论坛', '/forum', NULL, 'comment', 510, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 510);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 511, 1, 510, '我的收藏', '/forum/collections', 'forum/collections/index', 'star', 1, 'MENU', 'sys:forum:browse', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 511);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 512, 1, 510, '论坛管理', '/forum/manage', 'forum/manage/index', 'safety', 2, 'MENU', 'sys:forum:manage:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 512);

-- ---------------------------------------------------------------------
-- 5. 自检（手工执行时按需取消注释）
-- ---------------------------------------------------------------------
-- SELECT r.tenant_id, r.role_code, COUNT(*) AS perm_cnt
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--  WHERE r.role_code IN ('tenant_admin', 'forum_moderator') AND r.deleted = 0
--  GROUP BY r.tenant_id, r.role_code;
-- -- 期望 tenant_admin 11 项 / forum_moderator 9 项
-- 以 admin 登录后 GET /auth/v1/me，确认 menus 树包含「论坛」目录与 2 个子菜单
-- =====================================================================
