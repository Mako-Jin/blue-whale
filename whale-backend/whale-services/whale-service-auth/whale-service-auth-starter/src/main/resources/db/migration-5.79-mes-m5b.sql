-- =====================================================================
-- Wave 5.79 M5-B：MES 综合看板 权限/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.79-mes-m5b.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : whale-mes M5-B（综合看板聚合、SSE 推送、CSV 导出，M5 看板收口）。
-- 权限编号 : MES 预留 1100–1399；1380 已被 M1 生产看板占用，本脚本使用 1381–1383。
-- 菜单编号 : MES 预留 240–299；M5 已用 282–287，本脚本使用 288。
-- 授权口径 : 查看与 SSE 订阅（1381/1382）对全部 MES 角色开放；
--            CSV 导出（1383）仅 tenant_admin / mes_manager / mes_team_lead。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 综合看板权限 1381–1383
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1381, 0, 'sys:mes:dashboard:view', '查看 MES 综合看板', 'API', '/mes/dashboard', 'GET', 1381, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1381 OR perm_code = 'sys:mes:dashboard:view');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1382, 0, 'sys:mes:dashboard:stream', '订阅 MES 综合看板推送', 'API', '/mes/dashboard/stream', 'POST', 1382, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1382 OR perm_code = 'sys:mes:dashboard:stream');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1383, 0, 'sys:mes:dashboard:export', '导出 MES 综合看板', 'API', '/mes/dashboard/export', 'GET', 1383, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1383 OR perm_code = 'sys:mes:dashboard:export');

-- ---------------------------------------------------------------------
-- 2. 菜单 288（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 288, 1, 0, '综合看板', '/mes/dashboard', 'mes/dashboard', 'dashboard', 288, 'MENU', 'sys:mes:dashboard:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 288);

-- ---------------------------------------------------------------------
-- 3. 授权
-- ---------------------------------------------------------------------
-- 3.1 管理员 / 厂长：查看 + 订阅 + 导出
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'mes_manager')
        AND p.id BETWEEN 1381 AND 1383) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.2 班组长：查看 + 订阅 + 导出
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_team_lead'
        AND p.id BETWEEN 1381 AND 1383) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.3 计划员：查看 + 订阅
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_planner'
        AND p.id IN (1381, 1382)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.4 操作工 / 质量员 / 普通成员：仅查看 + 订阅
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_operator', 'mes_quality', 'mes_member')
        AND p.id IN (1381, 1382)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1381 AND 1383;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id = 288;
--   SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     WHERE rp.permission_id BETWEEN 1381 AND 1383
--     GROUP BY r.role_code ORDER BY r.role_code;
-- =====================================================================
