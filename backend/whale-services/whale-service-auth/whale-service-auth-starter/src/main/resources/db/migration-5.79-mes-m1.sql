-- =====================================================================
-- Wave 5.79 M1：MES 调度派工、生产执行与看板 权限/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.79-mes-m1.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : whale-mes M1（工单接收/拆分/派工/报工/在制品/异常/领退料/看板），
--            内网工单接收 POST /mes/internal/work-order 由网关门禁与内网令牌保护，不配权限。
-- 权限编号 : MES 预留 1100–1399；本脚本使用 1130–1133（调度派工）、
--            1180–1184（生产执行）、1380（生产看板）。
-- 菜单编号 : MES 预留 240–299；本脚本使用 245（调度派工）、246（生产执行）、247（生产看板）。
-- 授权口径 : 计划员 = 调度派工全量 + 执行只读 + 看板；
--            tenant_admin / mes_manager / mes_team_lead = 调度 + 执行 + 看板全量；
--            mes_operator = 调度只读 + 执行全量 + 看板；
--            mes_quality / mes_equipment / mes_member = 调度、执行只读 + 看板。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 调度派工权限 1130–1133
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1130, 0, 'sys:mes:dispatch:list', '查询 MES 调度任务', 'API', '/mes/dispatch/task/page', 'GET', 1130, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1130 OR perm_code = 'sys:mes:dispatch:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1131, 0, 'sys:mes:dispatch:split', 'MES 工单拆分', 'API', '/mes/dispatch/work-order/{id}/split', 'POST', 1131, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1131 OR perm_code = 'sys:mes:dispatch:split');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1132, 0, 'sys:mes:dispatch:add', 'MES 批量派工', 'API', '/mes/dispatch/dispatch', 'POST', 1132, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1132 OR perm_code = 'sys:mes:dispatch:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1133, 0, 'sys:mes:dispatch:withdraw', 'MES 撤回派工', 'API', '/mes/dispatch/dispatch/{id}/withdraw', 'POST', 1133, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1133 OR perm_code = 'sys:mes:dispatch:withdraw');

-- ---------------------------------------------------------------------
-- 2. 生产执行权限 1180–1184
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1180, 0, 'sys:mes:execution:list', '查询 MES 生产执行', 'API', '/mes/execution/report/page,/mes/execution/wip', 'GET', 1180, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1180 OR perm_code = 'sys:mes:execution:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1181, 0, 'sys:mes:execution:action', 'MES 任务执行动作', 'API', '/mes/execution/task/{id}/start,/mes/execution/task/{id}/pause,/mes/execution/task/{id}/resume,/mes/execution/task/{id}/complete,/mes/execution/task/{id}/scrap', 'POST', 1181, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1181 OR perm_code = 'sys:mes:execution:action');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1182, 0, 'sys:mes:execution:report', 'MES 生产报工', 'API', '/mes/execution/report', 'POST', 1182, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1182 OR perm_code = 'sys:mes:execution:report');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1183, 0, 'sys:mes:execution:material', 'MES 领退料登记', 'API', '/mes/execution/material-issue,/mes/execution/material-return', 'POST', 1183, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1183 OR perm_code = 'sys:mes:execution:material');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1184, 0, 'sys:mes:execution:exception', 'MES 异常上报与处理', 'API', '/mes/execution/exception,/mes/execution/exception/{id}/action', 'POST', 1184, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1184 OR perm_code = 'sys:mes:execution:exception');

-- ---------------------------------------------------------------------
-- 3. 生产看板权限 1380
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1380, 0, 'sys:mes:board:view', '查看 MES 生产看板', 'API', '/mes/dispatch/board', 'GET', 1380, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1380 OR perm_code = 'sys:mes:board:view');

-- ---------------------------------------------------------------------
-- 4. 菜单 245–247（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 245, 1, 0, '调度派工', '/mes/dispatch', 'mes/dispatch', 'schedule', 245, 'MENU', 'sys:mes:dispatch:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 245);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 246, 1, 0, '生产执行', '/mes/execution', 'mes/execution', 'play-circle', 246, 'MENU', 'sys:mes:execution:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 246);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 247, 1, 0, '生产看板', '/mes/board', 'mes/board', 'dashboard', 247, 'MENU', 'sys:mes:board:view', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 247);

-- ---------------------------------------------------------------------
-- 5. 授权
-- ---------------------------------------------------------------------
-- 5.1 计划员：调度全量 + 执行只读 + 看板
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_planner'
        AND (p.id BETWEEN 1130 AND 1133 OR p.id IN (1180, 1380))) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 5.2 tenant_admin / mes_manager / mes_team_lead：调度 + 执行 + 看板全量
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'mes_manager', 'mes_team_lead')
        AND (p.id BETWEEN 1130 AND 1133 OR p.id BETWEEN 1180 AND 1184 OR p.id = 1380)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 5.3 操作工：调度只读 + 执行全量 + 看板
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_operator'
        AND (p.id = 1130 OR p.id BETWEEN 1180 AND 1184 OR p.id = 1380)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 5.4 质量员 / 设备员 / 普通成员：调度、执行只读 + 看板
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_quality', 'mes_equipment', 'mes_member')
        AND p.id IN (1130, 1180, 1380)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id IN (1130,1131,1132,1133,1180,1181,1182,1183,1184,1380);
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 245 AND 247;
--   SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     WHERE rp.permission_id IN (1130,1131,1132,1133,1180,1181,1182,1183,1184,1380)
--     GROUP BY r.role_code ORDER BY r.role_code;
-- =====================================================================
