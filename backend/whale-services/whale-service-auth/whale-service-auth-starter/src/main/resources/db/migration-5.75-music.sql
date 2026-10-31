-- =====================================================================
-- Wave 5.75 M0：音乐平台服务（whale-music）权限、角色与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.75-music.sql
-- 幂等说明 : 权限、角色、授权、用户角色与菜单均为 NOT EXISTS 幂等插入，
--            可重复执行；不修改/删除既有数据。
-- 背景     : 所有登录用户均可上传歌曲；上传前必须签署《版权无争议协议》，
--            并选择公开（PUBLIC）或自听（PRIVATE）。公开作品不设上传前置审核，
--            运营事后复核并可下架。数据可见性由服务侧二次校验
--            （自听作品仅上传者可见；跨租户一律 404）。
-- 权限编号 : 已用到 279（doc M2b/M3），本脚本使用 280–302。
-- 菜单编号 : 已用到 38（文档模板），本脚本使用 39–44。
-- 角色编号 : 平台角色 25103 music_admin / 25104 music_member；
--            业务租户复制角色与授权 ID 采用「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : music_admin / tenant_admin 获得 280–302（23 项）；
--            music_member 获得用户侧 298–302（浏览/自建歌单/评论/收藏/上传）。
--            为满足「所有人可上传」，本脚本还将 music_member 幂等授予所有启用用户。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（280–302）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 280, 0, 'sys:music:artist:list', '查询歌手', 'API', '/music/artist/page,/music/artist/{id}', 'GET', 280, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 280 OR perm_code = 'sys:music:artist:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 281, 0, 'sys:music:artist:add', '新增歌手', 'API', '/music/artist', 'POST', 281, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 281 OR perm_code = 'sys:music:artist:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 282, 0, 'sys:music:artist:edit', '编辑歌手', 'API', '/music/artist/{id}', 'PUT', 282, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 282 OR perm_code = 'sys:music:artist:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 283, 0, 'sys:music:artist:delete', '删除歌手', 'API', '/music/artist/{id}', 'DELETE', 283, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 283 OR perm_code = 'sys:music:artist:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 284, 0, 'sys:music:album:list', '查询专辑', 'API', '/music/album/page,/music/album/{id}', 'GET', 284, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 284 OR perm_code = 'sys:music:album:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 285, 0, 'sys:music:album:add', '新增专辑', 'API', '/music/album', 'POST', 285, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 285 OR perm_code = 'sys:music:album:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 286, 0, 'sys:music:album:edit', '编辑专辑', 'API', '/music/album/{id}', 'PUT', 286, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 286 OR perm_code = 'sys:music:album:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 287, 0, 'sys:music:album:delete', '删除专辑', 'API', '/music/album/{id}', 'DELETE', 287, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 287 OR perm_code = 'sys:music:album:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 288, 0, 'sys:music:album:publish', '上架/下架专辑', 'API', '/music/album/{id}/publish,/music/album/{id}/offline', 'POST', 288, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 288 OR perm_code = 'sys:music:album:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 289, 0, 'sys:music:song:list', '查询歌曲', 'API', '/music/song/page,/music/song/{id}', 'GET', 289, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 289 OR perm_code = 'sys:music:song:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 290, 0, 'sys:music:song:add', '新增歌曲', 'API', '/music/song', 'POST', 290, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 290 OR perm_code = 'sys:music:song:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 291, 0, 'sys:music:song:edit', '编辑歌曲', 'API', '/music/song/{id}', 'PUT', 291, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 291 OR perm_code = 'sys:music:song:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 292, 0, 'sys:music:song:delete', '删除歌曲', 'API', '/music/song/{id}', 'DELETE', 292, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 292 OR perm_code = 'sys:music:song:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 293, 0, 'sys:music:song:publish', '发布/下架/版权下架歌曲', 'API', '/music/song/{id}/publish,/music/song/{id}/offline,/music/song/{id}/takedown', 'POST', 293, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 293 OR perm_code = 'sys:music:song:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 294, 0, 'sys:music:tag:manage', '管理音乐标签', 'API', '/music/tag,/music/tag/{id},/music/tag/list', 'GET,POST,PUT,DELETE', 294, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 294 OR perm_code = 'sys:music:tag:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 295, 0, 'sys:music:playlist:manage', '管理官方歌单', 'API', '/music/playlist/official,/music/playlist/official/{id}', 'GET,POST,PUT,DELETE', 295, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 295 OR perm_code = 'sys:music:playlist:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 296, 0, 'sys:music:comment:audit', '审核歌曲评论', 'API', '/music/comment/page,/music/comment/{id}/approve,/music/comment/{id}/hide', 'GET,POST', 296, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 296 OR perm_code = 'sys:music:comment:audit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 297, 0, 'sys:music:stat:view', '查看音乐统计', 'API', '/music/stat/overview', 'GET', 297, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 297 OR perm_code = 'sys:music:stat:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 298, 0, 'sys:music:browse', '浏览音乐内容', 'API', '/music/meta,/music/home,/music/search,/music/chart/hot,/music/chart/new,/music/song/page,/music/album/page,/music/artist/page,/music/playlist/page,/music/playlist/{id}', 'GET', 298, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 298 OR perm_code = 'sys:music:browse');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 299, 0, 'sys:music:playlist:own', '管理我的歌单', 'API', '/music/playlist,/music/playlist/{id},/music/playlist/{id}/song,/music/playlist/{id}/song/{songId},/music/playlist/{id}/sort', 'POST,PUT,DELETE', 299, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 299 OR perm_code = 'sys:music:playlist:own');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 300, 0, 'sys:music:comment:create', '发表歌曲评论', 'API', '/music/song/{id}/comment,/music/song/{id}/comment/page', 'GET,POST', 300, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 300 OR perm_code = 'sys:music:comment:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 301, 0, 'sys:music:like', '收藏歌曲', 'API', '/music/song/{id}/like,/music/my/likes', 'GET,POST,DELETE', 301, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 301 OR perm_code = 'sys:music:like');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 302, 0, 'sys:music:upload', '上传与管理我的作品', 'API', '/music/upload,/music/my/songs,/music/user/song,/music/user/song/{id},/music/copyright/agreement/accept,/music/copyright/agreement/current', 'GET,POST,PUT,DELETE', 302, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 302 OR perm_code = 'sys:music:upload');

-- ---------------------------------------------------------------------
-- 2. 平台租户业务角色模板（25103 / 25104）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25103, 1, 'music_admin', '音乐管理员', '曲库/专辑/官方歌单/评论审核/统计全量功能入口，可复核与下架公开作品', 253, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25103 OR (tenant_id = 1 AND role_code = 'music_admin'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25104, 1, 'music_member', '音乐用户', '浏览/上传/自听/自建歌单/评论/收藏功能入口；上传前须签署版权无争议协议', 254, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25104 OR (tenant_id = 1 AND role_code = 'music_member'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制音乐角色（仅 tenant_type='TENANT' 的启用租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'music_admin' AS role_code, '音乐管理员' AS role_name, '曲库/专辑/官方歌单/评论审核/统计全量功能入口，可复核与下架公开作品' AS description, 253 AS sort_no UNION ALL SELECT 'music_member', '音乐用户', '浏览/上传/自听/自建歌单/评论/收藏功能入口；上传前须签署版权无争议协议', 254 ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与全部业务租户）
--    music_admin / tenant_admin：280–302（23 项）
--    music_member：298–302（5 项用户侧能力）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 280 AND 302 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('music_admin', 'tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 298 AND 302 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'music_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 所有人可上传：将 music_member 授予全部启用用户（幂等）
--    按用户所属租户匹配同名角色；已存在用户-角色关系时跳过。
-- ---------------------------------------------------------------------
INSERT INTO sys_user_role (id, user_id, role_id)
SELECT src.id, src.user_id, src.role_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(u.id, ':', r.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, u.id AS user_id, r.id AS role_id FROM sys_user u JOIN sys_role r ON r.tenant_id = u.tenant_id AND r.role_code = 'music_member' AND r.deleted = 0 AND r.status = 1 WHERE u.deleted = 0 AND u.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role tgt WHERE tgt.id = src.id OR (tgt.user_id = src.user_id AND tgt.role_id = src.role_id));

-- ---------------------------------------------------------------------
-- 6. 菜单：音乐中心（39）与运营子菜单（40–44）
--    可见性由 /auth/me 按用户权限码过滤，无需菜单-角色授权表。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 39, 1, 0, '音乐中心', '/music', NULL, 'music', 39, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 39);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 40, 1, 39, '音乐曲库', '/music/songs', 'music/songs', 'music-note', 1, 'MENU', 'sys:music:song:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 40);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 41, 1, 39, '歌手与专辑', '/music/catalog', 'music/catalog', 'user', 2, 'MENU', 'sys:music:artist:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 41);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 42, 1, 39, '歌单与标签', '/music/playlists', 'music/playlists', 'playlist', 3, 'MENU', 'sys:music:playlist:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 42);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 43, 1, 39, '评论审核', '/music/comments', 'music/comments', 'comment', 4, 'MENU', 'sys:music:comment:audit', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 43);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 44, 1, 39, '播放统计', '/music/stats', 'music/stats', 'chart', 5, 'MENU', 'sys:music:stat:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 44);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 280 AND 302;
--   SELECT tenant_id, role_code FROM sys_role WHERE role_code IN ('music_admin','music_member') ORDER BY tenant_id;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 280 AND 302 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：music_admin / tenant_admin 各 23 项；music_member 各 5 项
--   SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
--   WHERE r.role_code = 'music_member' AND ur.deleted = 0;
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 39 AND 44;
-- =====================================================================
