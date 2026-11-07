-- =====================================================================
-- Wave 5.82 M3-P2：OA 网盘 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m3p2-drive.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增个人/部门网盘文件夹、文件登记、授权、下载与配额查询。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1743–1745。
-- 菜单编号 : OA 段 360–409；本脚本使用 387。
-- 授权口径 : oa_admin、oa_manager、tenant_admin 与 oa_member 均获得 1743–1745
--            （网盘为全员协作能力）。
-- 前置条件 : bluewhale_oa 已执行 V11__drive.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1743–1745
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1743, 0, 'sys:oa:drive:list', '查询网盘', 'API',
       '/oa/drive/folders,/oa/drive/files,/oa/drive/quota,/oa/drive/folders/{id}/acls', 'GET', 1743, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1743 OR perm_code = 'sys:oa:drive:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1744, 0, 'sys:oa:drive:manage', '管理网盘', 'API',
       '/oa/drive/folders,/oa/drive/folders/{id},/oa/drive/folders/{id}/acls,/oa/drive/files,/oa/drive/files/{id}', 'POST,PUT,DELETE', 1744, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1744 OR perm_code = 'sys:oa:drive:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1745, 0, 'sys:oa:drive:download', '下载网盘文件', 'API',
       '/oa/drive/files/{id}/download', 'GET', 1745, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1745 OR perm_code = 'sys:oa:drive:download');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'oa_manager', 'tenant_admin', 'oa_member') AND p.id BETWEEN 1743 AND 1745) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 387
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 387, 1, 0, '网盘', '/oa/collab/drive', 'collab/drive/index', 'folder', 387, 'MENU', 'sys:oa:drive:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 387);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 1743 AND 1745 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id = 387;
-- =====================================================================
