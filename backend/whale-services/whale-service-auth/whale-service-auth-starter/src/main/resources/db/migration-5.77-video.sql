-- =====================================================================
-- Wave 5.77 M0：视频平台服务（whale-video）权限、角色与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.77-video.sql
-- 幂等说明 : 权限、角色、授权、用户角色与菜单均为 NOT EXISTS 幂等插入，
--            可重复执行；不修改/删除既有数据。
-- 背景     : 短视频 / 视频由登录用户上传（上传前须签署《内容合法与版权无争议声明》），
--            电影仅运营入库与发布；跨租户一律按不存在处理。
-- 权限编号 : 已用至 304（音乐申诉），本脚本顺延使用 305–322（18 项）。
-- 菜单编号 : 已用至 45（音乐申诉），本脚本使用 46–52。
-- 角色编号 : 平台角色 25105 video_admin / 25106 video_member；
--            业务租户复制角色与授权 ID 采用「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : video_admin / tenant_admin 获得 305–322（18 项）；
--            video_member 获得用户侧 314–318（浏览/上传/点赞/收藏/评论），
--            并追加 32 sys:file:upload（分片上传视频与封面落 whale-file 所需）。
--            为满足「所有登录用户可上传」，本脚本将 video_member 幂等授予所有启用用户。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（305–322）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 305, 0, 'sys:video:content:list', '查询视频内容', 'API', '/video/admin/content/page,/video/admin/movies/page,/video/admin/creator/page', 'GET', 305, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 305 OR perm_code = 'sys:video:content:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 306, 0, 'sys:video:content:add', '新增视频内容', 'API', '/video/admin/movies,/video/admin/series,/video/admin/collections', 'POST', 306, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 306 OR perm_code = 'sys:video:content:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 307, 0, 'sys:video:content:edit', '编辑视频内容', 'API', '/video/admin/movies/{id},/video/admin/series/{id},/video/admin/collections/{id},/video/admin/creator/{id}', 'PUT', 307, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 307 OR perm_code = 'sys:video:content:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 308, 0, 'sys:video:content:delete', '删除视频内容', 'API', '/video/admin/movies/{id},/video/admin/series/{id},/video/admin/collections/{id}', 'DELETE', 308, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 308 OR perm_code = 'sys:video:content:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 309, 0, 'sys:video:content:publish', '发布视频内容', 'API', '/video/admin/content/{id}/publish', 'POST', 309, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 309 OR perm_code = 'sys:video:content:publish');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 310, 0, 'sys:video:content:offline', '下架视频内容', 'API', '/video/admin/content/{id}/offline', 'POST', 310, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 310 OR perm_code = 'sys:video:content:offline');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 311, 0, 'sys:video:content:takedown', '版权下架视频内容', 'API', '/video/admin/content/{id}/takedown', 'POST', 311, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 311 OR perm_code = 'sys:video:content:takedown');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 312, 0, 'sys:video:comment:audit', '审核视频评论', 'API', '/video/admin/comment/page,/video/admin/comment/{id}/approve,/video/admin/comment/{id}/hide', 'GET,POST', 312, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 312 OR perm_code = 'sys:video:comment:audit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 313, 0, 'sys:video:stat:view', '查看视频统计', 'API', '/video/admin/stat/overview', 'GET', 313, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 313 OR perm_code = 'sys:video:stat:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 314, 0, 'sys:video:browse', '浏览视频内容', 'API', '/video/meta,/video/home,/video/search,/video/categories,/video/tags,/video/content/{id},/video/shorts/feed,/video/shorts/{id},/video/videos/page,/video/videos/{id},/video/movies/page,/video/movies/{id},/video/series/page,/video/series/{id},/video/collections/{id}', 'GET', 314, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 314 OR perm_code = 'sys:video:browse');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 315, 0, 'sys:video:upload', '上传与管理我的视频', 'API', '/video/user/shorts,/video/user/shorts/{id},/video/user/shorts/{id}/publish,/video/user/shorts/{id}/offline,/video/user/videos,/video/user/videos/{id},/video/user/videos/{id}/publish,/video/user/videos/{id}/offline,/video/my/shorts,/video/my/videos,/video/declaration/current,/video/declaration/accept', 'ALL', 315, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 315 OR perm_code = 'sys:video:upload');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 316, 0, 'sys:video:like', '点赞视频内容', 'API', '/video/content/{id}/like,/video/my/likes', 'GET,POST,DELETE', 316, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 316 OR perm_code = 'sys:video:like');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 317, 0, 'sys:video:favorite', '收藏视频内容', 'API', '/video/content/{id}/favorite,/video/my/favorites', 'GET,POST,DELETE', 317, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 317 OR perm_code = 'sys:video:favorite');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 318, 0, 'sys:video:comment:create', '发表视频评论', 'API', '/video/content/{id}/comment,/video/content/{id}/comment/page,/video/comment/{id}/like', 'GET,POST,DELETE', 318, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 318 OR perm_code = 'sys:video:comment:create');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 319, 0, 'sys:video:category:manage', '管理视频分类与标签', 'API', '/video/admin/categories,/video/admin/categories/{id},/video/admin/tags,/video/admin/tags/{id}', 'ALL', 319, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 319 OR perm_code = 'sys:video:category:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 320, 0, 'sys:video:series:manage', '管理视频系列', 'API', '/video/admin/series,/video/admin/series/{id}', 'ALL', 320, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 320 OR perm_code = 'sys:video:series:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 321, 0, 'sys:video:movie:manage', '管理电影与授权', 'API', '/video/admin/movies,/video/admin/movies/{id},/video/admin/collections,/video/admin/collections/{id},/video/admin/collections/{id}/item', 'ALL', 321, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 321 OR perm_code = 'sys:video:movie:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 322, 0, 'sys:video:creator:manage', '管理视频创作者', 'API', '/video/admin/creator/page,/video/admin/creator/{id}', 'GET,PUT', 322, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 322 OR perm_code = 'sys:video:creator:manage');

-- ---------------------------------------------------------------------
-- 2. 平台租户业务角色模板（25105 / 25106）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25105, 1, 'video_admin', '视频管理员', '短视频/视频/系列/电影/分类/评论审核/统计全量功能入口，可发布、下架与版权下架', 255, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25105 OR (tenant_id = 1 AND role_code = 'video_admin'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25106, 1, 'video_member', '视频用户', '浏览/上传短视频与视频/点赞/收藏/评论功能入口；上传前须签署内容合法与版权无争议声明', 256, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25106 OR (tenant_id = 1 AND role_code = 'video_member'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制视频角色（仅 tenant_type='TENANT' 的启用租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'video_admin' AS role_code, '视频管理员' AS role_name, '短视频/视频/系列/电影/分类/评论审核/统计全量功能入口，可发布、下架与版权下架' AS description, 255 AS sort_no UNION ALL SELECT 'video_member', '视频用户', '浏览/上传短视频与视频/点赞/收藏/评论功能入口；上传前须签署内容合法与版权无争议声明', 256 ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权（按角色编码匹配，覆盖平台与全部业务租户）
--    video_admin / tenant_admin：305–322（18 项）
--    video_member：314–318（5 项用户侧能力）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 305 AND 322 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('video_admin', 'tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 314 AND 318 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'video_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 4.1 上传能力依赖（幂等）
--     video_member 需要 32 sys:file:upload（分片上传视频与封面到 whale-file）。
--     仅新增「上传」能力，不含电影入库等运营权限。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id = 32 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'video_member') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 4.2 所有登录用户可上传：将 video_member 授予全部启用用户（幂等）
--     按用户所属租户匹配同名角色；已存在用户-角色关系时跳过。
-- ---------------------------------------------------------------------
INSERT INTO sys_user_role (id, user_id, role_id)
SELECT src.id, src.user_id, src.role_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(u.id, ':', r.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, u.id AS user_id, r.id AS role_id FROM sys_user u JOIN sys_role r ON r.tenant_id = u.tenant_id AND r.role_code = 'video_member' AND r.deleted = 0 AND r.status = 1 WHERE u.deleted = 0 AND u.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role tgt WHERE tgt.id = src.id OR (tgt.user_id = src.user_id AND tgt.role_id = src.role_id));

-- ---------------------------------------------------------------------
-- 5. 菜单：视频中心（46）与运营子菜单（47–52）
--    可见性由 /auth/me 按用户权限码过滤，无需菜单-角色授权表。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 46, 1, 0, '视频中心', '/video', NULL, 'video-camera', 46, 'DIR', NULL, 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 46);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 47, 1, 46, '短视频管理', '/video/shorts', 'video/content-list', 'play-circle', 1, 'MENU', 'sys:video:content:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 47);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 48, 1, 46, '视频管理', '/video/videos', 'video/content-list', 'play-square', 2, 'MENU', 'sys:video:content:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 48);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 49, 1, 46, '电影管理', '/video/movies', 'video/movie-list', 'film', 3, 'MENU', 'sys:video:movie:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 49);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 50, 1, 46, '分类 / 系列 / 片单', '/video/catalog', 'video/catalog', 'appstore', 4, 'MENU', 'sys:video:category:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 50);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 51, 1, 46, '评论审核', '/video/comments', 'video/comment-audit', 'comment', 5, 'MENU', 'sys:video:comment:audit', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 51);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 52, 1, 46, '播放统计', '/video/stats', 'video/stat', 'chart', 6, 'MENU', 'sys:video:stat:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 52);

-- ---------------------------------------------------------------------
-- 6. M4 接口契约增量（幂等）
--    电影 / 片单端点复用既有权限点，只追加 path；按 id 精确更新，
--    重复执行结果一致，不影响已存在的路径配置。
-- ---------------------------------------------------------------------
UPDATE sys_permission
SET path = '/video/admin/content/page,/video/admin/movies/page,/video/admin/collections/page,/video/admin/creator/page'
WHERE id = 305 AND perm_code = 'sys:video:content:list';

UPDATE sys_permission
SET path = '/video/admin/content/{id}/publish,/video/admin/movies/{id}/publish'
WHERE id = 309 AND perm_code = 'sys:video:content:publish';

UPDATE sys_permission
SET path = '/video/admin/content/{id}/offline,/video/admin/movies/{id}/offline'
WHERE id = 310 AND perm_code = 'sys:video:content:offline';

UPDATE sys_permission
SET path = '/video/admin/content/{id}/takedown,/video/admin/movies/{id}/takedown'
WHERE id = 311 AND perm_code = 'sys:video:content:takedown';

UPDATE sys_permission
SET path = '/video/meta,/video/home,/video/search,/video/rankings,/video/categories,/video/tags,/video/content/{id},/video/shorts/feed,/video/shorts/{id},/video/videos/page,/video/videos/{id},/video/movies/page,/video/movies/{id},/video/movies/{id}/poster,/video/movies/{id}/backdrop,/video/series/page,/video/series/{id},/video/collections/page,/video/collections/{id},/video/collections/{id}/cover'
WHERE id = 314 AND perm_code = 'sys:video:browse';

UPDATE sys_permission
SET path = '/video/admin/movies,/video/admin/movies/{id},/video/admin/movies/{id}/publish,/video/admin/movies/{id}/offline,/video/admin/movies/{id}/takedown,/video/admin/collections,/video/admin/collections/{id},/video/admin/collections/{id}/publish,/video/admin/collections/{id}/offline,/video/admin/collections/{id}/item,/video/admin/collections/{id}/item/{contentId}'
WHERE id = 321 AND perm_code = 'sys:video:movie:manage';

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 305 AND 322;
--   SELECT tenant_id, role_code FROM sys_role WHERE role_code IN ('video_admin','video_member') ORDER BY tenant_id;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 305 AND 322 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
--   -- 期望：video_admin / tenant_admin 各 18 项；video_member 各 5 项
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id BETWEEN 46 AND 52;
-- =====================================================================
