-- =====================================================================
-- Wave 5.79 M5：MES 人员绩效 权限/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.79-mes-m5.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : whale-mes M5（班组成员、排班分配、交接班、工时记录、绩效统计、技能矩阵）。
-- 权限编号 : MES 预留 1100–1399；本脚本使用 1350–1357。
-- 菜单编号 : MES 预留 240–299；本脚本使用 282–287。
-- 角色约定 : mes_team_lead（角色 ID 20707）为班组级核心角色，授予人员绩效全量。
-- 授权口径 : tenant_admin / mes_manager / mes_team_lead = 人员绩效全量（含月度锁定）；
--            mes_planner = 查询 + 绩效汇总；
--            mes_operator = 查询 + 交接班 + 工时登记；
--            mes_quality / mes_member = 仅人员绩效查询。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 人员绩效权限 1350–1357
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1350, 0, 'sys:mes:workforce:list', '查询 MES 人员绩效数据', 'API', '/mes/workforce/team-member/page,/mes/workforce/shift-assignment/page,/mes/workforce/handover/page,/mes/workforce/work-hour/page,/mes/workforce/performance/page,/mes/workforce/skill/page', 'GET', 1350, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1350 OR perm_code = 'sys:mes:workforce:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1351, 0, 'sys:mes:workforce:team', 'MES 班组成员维护', 'API', '/mes/workforce/team-member,/mes/workforce/team-member/{id}/status', 'POST', 1351, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1351 OR perm_code = 'sys:mes:workforce:team');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1352, 0, 'sys:mes:workforce:shift', 'MES 排班分配维护', 'API', '/mes/workforce/shift-assignment,/mes/workforce/shift-assignment/{id}/cancel', 'POST', 1352, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1352 OR perm_code = 'sys:mes:workforce:shift');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1353, 0, 'sys:mes:workforce:handover', 'MES 交接班维护', 'API', '/mes/workforce/handover,/mes/workforce/handover/{id}/confirm', 'POST', 1353, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1353 OR perm_code = 'sys:mes:workforce:handover');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1354, 0, 'sys:mes:workforce:work-hour', 'MES 工时登记与作废', 'API', '/mes/workforce/work-hour,/mes/workforce/work-hour/{id}/void', 'POST', 1354, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1354 OR perm_code = 'sys:mes:workforce:work-hour');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1355, 0, 'sys:mes:workforce:performance', 'MES 绩效统计汇总', 'API', '/mes/workforce/performance/aggregate', 'POST', 1355, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1355 OR perm_code = 'sys:mes:workforce:performance');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1356, 0, 'sys:mes:workforce:lock', 'MES 月度绩效锁定', 'API', '/mes/workforce/performance/lock', 'POST', 1356, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1356 OR perm_code = 'sys:mes:workforce:lock');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1357, 0, 'sys:mes:workforce:skill', 'MES 技能矩阵维护', 'API', '/mes/workforce/skill', 'POST', 1357, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1357 OR perm_code = 'sys:mes:workforce:skill');

-- ---------------------------------------------------------------------
-- 2. 菜单 282–287（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 282, 1, 0, '班组成员', '/mes/workforce/team', 'mes/workforce/team', 'team', 282, 'MENU', 'sys:mes:workforce:team', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 282);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 283, 1, 0, '排班管理', '/mes/workforce/shift', 'mes/workforce/shift', 'schedule', 283, 'MENU', 'sys:mes:workforce:shift', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 283);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 284, 1, 0, '交接班', '/mes/workforce/handover', 'mes/workforce/handover', 'swap', 284, 'MENU', 'sys:mes:workforce:handover', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 284);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 285, 1, 0, '工时管理', '/mes/workforce/work-hour', 'mes/workforce/work-hour', 'clock-circle', 285, 'MENU', 'sys:mes:workforce:work-hour', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 285);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 286, 1, 0, '绩效统计', '/mes/workforce/performance', 'mes/workforce/performance', 'bar-chart', 286, 'MENU', 'sys:mes:workforce:performance', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 286);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 287, 1, 0, '技能矩阵', '/mes/workforce/skill', 'mes/workforce/skill', 'star', 287, 'MENU', 'sys:mes:workforce:skill', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 287);

-- ---------------------------------------------------------------------
-- 3. 授权
-- ---------------------------------------------------------------------
-- 3.1 管理员 / 厂长 / 班组长（20707）：人员绩效全量（含月度锁定）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'mes_manager', 'mes_team_lead')
        AND p.id BETWEEN 1350 AND 1357) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.2 计划员：查询 + 绩效汇总
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_planner'
        AND p.id IN (1350, 1355)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.3 操作工：查询 + 交接班 + 工时登记
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_operator'
        AND p.id IN (1350, 1353, 1354)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.4 质量员 / 普通成员：仅人员绩效查询
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_quality', 'mes_member')
        AND p.id = 1350) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1350 AND 1357;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 282 AND 287;
--   SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     WHERE rp.permission_id BETWEEN 1350 AND 1357
--     GROUP BY r.role_code ORDER BY r.role_code;
-- =====================================================================
