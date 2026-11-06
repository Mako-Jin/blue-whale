-- =====================================================================
-- Wave 5.80 M1B-4：PDM 零件去重与合并权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-part-merge.sql
-- 幂等说明 : 权限、授权与菜单均为 NOT EXISTS 幂等执行，可重复执行；不删除任何数据。
-- 背景     : 零件去重候选审阅与人工合并（源件停用 + 草稿 BOM 引用改写）。
-- 权限编号 : PDM 段 930–949；940 已被 BOM 导出占用，本脚本使用 941–942。
-- 菜单编号 : PDM 段 180–219；本脚本使用 187。
-- 授权口径 : tenant_admin / pdm_manager / pdm_engineer 获得 941–942；
--            pdm_member 仅获得查询 941。
-- 前置条件 : 已执行 migration-5.80-pdm-part.sql 与 bluewhale_pdm V13__part_merge.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 941–942
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 941, 0, 'sys:pdm:part:dedup:list', '查看零件去重候选', 'API', '/pdm/part/dedup/candidates', 'GET', 941, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 941 OR perm_code = 'sys:pdm:part:dedup:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 942, 0, 'sys:pdm:part:merge', '合并零件', 'API', '/pdm/part/merge', 'POST', 942, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 942 OR perm_code = 'sys:pdm:part:merge');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_engineer') AND p.id IN (941, 942)) OR (r.role_code = 'pdm_member' AND p.id = 941) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 187, 1, 0, '零件去重', '/pdm/part/dedup', 'pdm/part/dedup', 'merge-cells', 187, 'MENU', 'sys:pdm:part:dedup:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 187);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id IN (941, 942);
--   -- 期望：2 行 sys:pdm:part:dedup:list / sys:pdm:part:merge。
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id = 187;
--   -- 期望：零件去重 / /pdm/part/dedup / sys:pdm:part:dedup:list。
-- =====================================================================
