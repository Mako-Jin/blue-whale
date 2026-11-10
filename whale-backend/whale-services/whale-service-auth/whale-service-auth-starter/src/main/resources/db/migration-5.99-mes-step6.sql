-- =====================================================================
-- 五服务分步计划 Step 6a：MES 有限产能排程 / 谱系冲销 / 设备维度追溯
-- 权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.99-mes-step6.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 权限编号 : MES 预留 1100–1399；1381–1383 已被 M5-B 占用，本脚本使用 1390–1393。
-- 菜单编号 : MES 预留 240–299；288 已被 M5-B 占用，本脚本使用 289。
-- 授权口径 : 排程查询/生成（1390/1391）对管理员、厂长、计划员、班组长开放；
--            谱系冲销（1392）仅管理员、厂长、班组长；设备追溯（1393）对全部 MES 角色开放。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限 1390–1393
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1390, 0, 'sys:mes:scheduling:list', '查看 MES 生产排程', 'API', '/mes/dispatch/schedule/plan/page', 'GET', 1390, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1390 OR perm_code = 'sys:mes:scheduling:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1391, 0, 'sys:mes:scheduling:manage', '生成与锁定 MES 生产排程', 'API', '/mes/dispatch/schedule/generate', 'POST', 1391, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1391 OR perm_code = 'sys:mes:scheduling:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1392, 0, 'sys:mes:trace:reverse', '冲销 MES 批次谱系', 'API', '/mes/trace/trace/link/*/reverse', 'POST', 1392, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1392 OR perm_code = 'sys:mes:trace:reverse');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1393, 0, 'sys:mes:trace:equipment', '设备维度追溯', 'API', '/mes/trace/trace/equipment/*', 'GET', 1393, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1393 OR perm_code = 'sys:mes:trace:equipment');

-- ---------------------------------------------------------------------
-- 2. 菜单 289（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 289, 1, 0, '生产排程', '/mes/dispatch/schedule', 'mes/dispatch/schedule', 'schedule', 289, 'MENU', 'sys:mes:scheduling:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 289);

-- ---------------------------------------------------------------------
-- 3. 授权
-- ---------------------------------------------------------------------
-- 3.1 管理员 / 厂长：全部四项
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'mes_manager')
        AND p.id BETWEEN 1390 AND 1393) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.2 计划员：排程查询 / 生成锁定
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_planner'
        AND p.id IN (1390, 1391)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.3 班组长：排程查询 / 生成锁定 / 谱系冲销 / 设备追溯
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_team_lead'
        AND p.id BETWEEN 1390 AND 1393) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.4 质量员：设备追溯
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_quality'
        AND p.id = 1393) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.5 操作工 / 普通成员：设备追溯
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_operator', 'mes_member')
        AND p.id = 1393) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1390 AND 1393;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id = 289;
--   SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     WHERE rp.permission_id BETWEEN 1390 AND 1393
--     GROUP BY r.role_code ORDER BY r.role_code;
-- =====================================================================
