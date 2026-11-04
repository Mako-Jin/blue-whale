-- =====================================================================
-- Wave 5.79 M4：MES 设备与数据采集 权限/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.79-mes-m4.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : whale-mes M4（设备台账/状态/停机、保养、维修、备件、OEE、采集设备/采集点/样本/看板/归档）。
-- 权限编号 : MES 预留 1100–1399；本脚本使用 1300–1307。
-- 菜单编号 : MES 预留 240–299；本脚本使用 274–281。
-- 授权口径 : mes_equipment / tenant_admin / mes_manager / mes_team_lead = 设备与采集全量；
--            mes_planner = 设备与采集查询；
--            mes_operator = 查询 + 设备状态变更；
--            mes_quality / mes_member = 仅设备与采集查询。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 设备与采集权限 1300–1307
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1300, 0, 'sys:mes:equipment:list', '查询 MES 设备与采集数据', 'API', '/mes/equipment/page,/mes/equipment/{id},/mes/equipment/gate,/mes/equipment/status-event/page,/mes/equipment/downtime/page,/mes/equipment/maintenance/plan/page,/mes/equipment/maintenance/order/page,/mes/equipment/repair/page,/mes/equipment/spare-part/page,/mes/equipment/spare-part/stock/page,/mes/equipment/spare-part/movement/page,/mes/equipment/oee/page,/mes/collect/device/page,/mes/collect/point/page,/mes/collect/sample/page,/mes/collect/board/page,/mes/collect/board/{boardCode}/data,/mes/collect/archive/page', 'GET', 1300, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1300 OR perm_code = 'sys:mes:equipment:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1301, 0, 'sys:mes:equipment:manage', 'MES 设备台账维护', 'API', '/mes/equipment', 'POST', 1301, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1301 OR perm_code = 'sys:mes:equipment:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1302, 0, 'sys:mes:equipment:status', 'MES 设备状态变更', 'API', '/mes/equipment/{id}/status', 'POST', 1302, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1302 OR perm_code = 'sys:mes:equipment:status');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1303, 0, 'sys:mes:equipment:maintenance', 'MES 保养计划与工单', 'API', '/mes/equipment/maintenance/plan,/mes/equipment/maintenance/order,/mes/equipment/maintenance/order/{id}/start,/mes/equipment/maintenance/order/{id}/complete,/mes/equipment/maintenance/order/{id}/accept', 'POST', 1303, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1303 OR perm_code = 'sys:mes:equipment:maintenance');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1304, 0, 'sys:mes:equipment:repair', 'MES 维修工单处理', 'API', '/mes/equipment/repair,/mes/equipment/repair/{id}/assign,/mes/equipment/repair/{id}/start,/mes/equipment/repair/{id}/verify,/mes/equipment/repair/{id}/close', 'POST', 1304, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1304 OR perm_code = 'sys:mes:equipment:repair');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1305, 0, 'sys:mes:equipment:spare-part', 'MES 备件与库存', 'API', '/mes/equipment/spare-part,/mes/equipment/spare-part/stock/in,/mes/equipment/spare-part/stock/out', 'POST', 1305, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1305 OR perm_code = 'sys:mes:equipment:spare-part');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1306, 0, 'sys:mes:equipment:oee', 'MES OEE 快照计算', 'API', '/mes/equipment/oee/snapshot', 'POST', 1306, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1306 OR perm_code = 'sys:mes:equipment:oee');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1307, 0, 'sys:mes:collect:manage', 'MES 采集设备/采集点/看板/归档维护', 'API', '/mes/collect/device,/mes/collect/device/{deviceCode}/heartbeat,/mes/collect/point,/mes/collect/board,/mes/collect/archive,/mes/collect/archive/{id}/run', 'POST', 1307, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1307 OR perm_code = 'sys:mes:collect:manage');

-- ---------------------------------------------------------------------
-- 2. 菜单 274–281（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 274, 1, 0, '设备台账', '/mes/equipment/ledger', 'mes/equipment/ledger', 'tool', 274, 'MENU', 'sys:mes:equipment:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 274);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 275, 1, 0, '设备状态', '/mes/equipment/status', 'mes/equipment/status', 'dashboard', 275, 'MENU', 'sys:mes:equipment:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 275);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 276, 1, 0, '保养管理', '/mes/equipment/maintenance', 'mes/equipment/maintenance', 'schedule', 276, 'MENU', 'sys:mes:equipment:maintenance', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 276);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 277, 1, 0, '维修管理', '/mes/equipment/repair', 'mes/equipment/repair', 'build', 277, 'MENU', 'sys:mes:equipment:repair', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 277);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 278, 1, 0, '备件管理', '/mes/equipment/spare-part', 'mes/equipment/spare-part', 'inbox', 278, 'MENU', 'sys:mes:equipment:spare-part', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 278);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 279, 1, 0, 'OEE 分析', '/mes/equipment/oee', 'mes/equipment/oee', 'pie-chart', 279, 'MENU', 'sys:mes:equipment:oee', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 279);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 280, 1, 0, '采集点管理', '/mes/collect/point', 'mes/collect/point', 'api', 280, 'MENU', 'sys:mes:collect:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 280);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 281, 1, 0, '数据看板与归档', '/mes/collect/board', 'mes/collect/board', 'dashboard', 281, 'MENU', 'sys:mes:collect:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 281);

-- ---------------------------------------------------------------------
-- 3. 授权
-- ---------------------------------------------------------------------
-- 3.1 设备员 / 管理员 / 厂长 / 班组长：设备与采集全量
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_equipment', 'tenant_admin', 'mes_manager', 'mes_team_lead')
        AND p.id BETWEEN 1300 AND 1307) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.2 计划员：设备与采集查询
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_planner'
        AND p.id = 1300) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.3 操作工：查询 + 设备状态变更
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'mes_operator'
        AND p.id IN (1300, 1302)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.4 质量员 / 普通成员：仅设备与采集查询
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id
      FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_quality', 'mes_member')
        AND p.id = 1300) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1300 AND 1307;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 274 AND 281;
--   SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     WHERE rp.permission_id BETWEEN 1300 AND 1307
--     GROUP BY r.role_code ORDER BY r.role_code;
-- =====================================================================
