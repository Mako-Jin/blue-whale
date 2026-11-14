-- =============================================================================
-- 文件        : V30__form_data_export_permission.sql
-- 说明        : 表单数据导出独立权限码 sys:form:data:export
--
-- 背景：
--   FormDataController#export 原先复用 sys:form:data:list，导致「只能查」的角色也能
--   批量导出，无法单独收口数据外带。本脚本登记独立权限点，并给已持有查询权限的角色
--   补授权，保证升级前后既有能力不变（后续可在角色管理中单独回收导出能力）。
--
-- 幂等：
--   - sys_permission 判重键 = perm_code（uk_perm_code），已存在则跳过；
--   - 授权按 perm_code 联查角色现有 sys:form:data:list 归属，
--     主键 MD5 派生 + NOT EXISTS 判重，可重复执行。
--
-- 权限编号 : 2420 = 表单数据导出（sys:form:data:export）
--            原取值 281 与 migration-5.75-music.sql 的 sys:music:artist:list 争用，
--            号段扫描（Code Quality · 权限/菜单号段）判 ERROR，故改为 2420。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点：表单数据导出
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2420, 0, 'sys:form:data:export', '导出表单数据', 'API', '/form/data/export', 'GET', 2420, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:form:data:export');

-- ---------------------------------------------------------------------
-- 2. 给已持有 sys:form:data:list 的角色补授导出权限
--    按 perm_code 匹配，不按权限主键写死，避免与存量数据主键不一致时漏授
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(rp.role_id, ':', pexport.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           rp.role_id, pexport.id AS permission_id
    FROM sys_role_permission rp
    JOIN sys_permission plist ON plist.id = rp.permission_id
         AND plist.perm_code = 'sys:form:data:list' AND plist.deleted = 0
    JOIN sys_permission pexport ON pexport.perm_code = 'sys:form:data:export' AND pexport.deleted = 0
    WHERE rp.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
