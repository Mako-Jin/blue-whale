-- =====================================================================
-- Wave 5.75 M5 待做补齐：音乐版权下架申诉权限与菜单迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.75-m5-appeal.sql
-- 幂等说明 : 权限、授权、菜单均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除既有数据。
-- 背景     : M5-3 申诉接口落地后，运营复核需要独立权限点 304
--            sys:music:appeal:audit（查看申诉队列 / 通过 / 驳回）；
--            用户侧提交申诉复用既有 303 sys:music:upload（已授予全部启用用户），
--            因此无需新增用户侧权限。
-- 权限编号 : 顺延自 migration-5.75-music.sql 的 281–303，本脚本使用 304。
-- 菜单编号 : 顺延自 39–44，本脚本使用 45（音乐中心下运营子菜单）。
-- 授权口径 : music_admin / tenant_admin 获得 304；
--            music_member 不获得复核权限，避免上传者自审自批。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（304）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 304, 0, 'sys:music:appeal:audit', '审核版权下架申诉', 'API', '/music/appeal/page,/music/appeal/{id}/approve,/music/appeal/{id}/reject', 'GET,POST', 304, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 304 OR perm_code = 'sys:music:appeal:audit');

-- ---------------------------------------------------------------------
-- 2. 授权：music_admin / tenant_admin（覆盖平台与全部业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id = 304 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('music_admin', 'tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单：音乐中心（39）下的「版权申诉」（45）
--    可见性由 /auth/me 按用户权限码过滤，无需菜单-角色授权表。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 45, 1, 39, '版权申诉', '/music/appeals', 'music/appeals', 'audit', 6, 'MENU', 'sys:music:appeal:audit', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 45);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id = 304;
--   SELECT r.tenant_id, r.role_code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id = 304 AND r.deleted = 0 AND r.status = 1;
--   -- 期望：仅 music_admin / tenant_admin
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 45;
-- =====================================================================
