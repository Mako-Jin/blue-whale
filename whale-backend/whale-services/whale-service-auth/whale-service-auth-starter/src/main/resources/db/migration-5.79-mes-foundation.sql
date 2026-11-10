-- =====================================================================
-- Wave 5.79 M0：MES 车间主数据与工单模型升级 权限/角色/菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.79-mes-foundation.sql
-- 幂等说明 : 权限、角色、菜单与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改/删除任何既有数据。
-- 背景     : whale-mes 模块化单体落地 M0（车间主数据 + 工单生命周期），
--            统一主数据接口 /mes/master-data/{kind}/**，工单模型升级为 ERP 下达执行工单。
-- 权限编号 : MES 预留 1100–1399；本脚本使用 1100–1103（基础主数据 CRUD），
--            工单仍复用存量 207–210。
-- 菜单编号 : MES 预留 240–299；本脚本使用 240（基础主数据）、241（生产工单）。
-- 角色编号 : 平台角色 20703–20707；业务租户复制角色与授权 ID 采用
--            「业务键 MD5 前 15 位」，重复执行稳定。
-- 授权口径 : tenant_admin / mes_manager / mes_planner 获得 1100–1103（主数据全量）；
--            mes_member / mes_operator / mes_quality / mes_equipment / mes_team_lead
--            仅获得 1100（主数据只读）；
--            工单权限沿用 207–210，按岗位下发（计划员全量、操作工/班组长不含删除、
--            质量员/设备员只读）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 基础主数据权限 1100–1103
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1100, 0, 'sys:mes:master:list', '查询 MES 基础主数据', 'API', '/mes/master-data/{kind}/page,/mes/master-data/{kind}/options,/mes/master-data/{kind}/{id}', 'GET', 1100, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1100 OR perm_code = 'sys:mes:master:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1101, 0, 'sys:mes:master:add', '新增 MES 基础主数据', 'API', '/mes/master-data/{kind}', 'POST', 1101, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1101 OR perm_code = 'sys:mes:master:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1102, 0, 'sys:mes:master:edit', '编辑 MES 基础主数据', 'API', '/mes/master-data/{kind}/{id}', 'PUT', 1102, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1102 OR perm_code = 'sys:mes:master:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1103, 0, 'sys:mes:master:delete', '删除 MES 基础主数据', 'API', '/mes/master-data/{kind}/{id}', 'DELETE', 1103, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1103 OR perm_code = 'sys:mes:master:delete');

-- ---------------------------------------------------------------------
-- 2. 平台租户业务角色模板 20703–20707
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20703, 1, 'mes_planner', 'MES 生产计划员', '车间主数据全量 + 生产工单全量（含删除）', 209, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20703 OR (tenant_id = 1 AND role_code = 'mes_planner'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20704, 1, 'mes_operator', 'MES 操作工', '车间主数据只读 + 生产工单查询/新增/编辑', 210, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20704 OR (tenant_id = 1 AND role_code = 'mes_operator'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20705, 1, 'mes_quality', 'MES 质量员', '车间主数据只读 + 生产工单只读', 211, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20705 OR (tenant_id = 1 AND role_code = 'mes_quality'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20706, 1, 'mes_equipment', 'MES 设备员', '车间主数据只读 + 生产工单只读', 212, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20706 OR (tenant_id = 1 AND role_code = 'mes_equipment'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20707, 1, 'mes_team_lead', 'MES 班组长', '车间主数据只读 + 生产工单查询/新增/编辑', 213, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20707 OR (tenant_id = 1 AND role_code = 'mes_team_lead'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制 MES 业务角色
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'mes_planner' AS role_code, 'MES 生产计划员' AS role_name, '车间主数据全量 + 生产工单全量（含删除）' AS description, 209 AS sort_no UNION ALL SELECT 'mes_operator', 'MES 操作工', '车间主数据只读 + 生产工单查询/新增/编辑', 210 UNION ALL SELECT 'mes_quality', 'MES 质量员', '车间主数据只读 + 生产工单只读', 211 UNION ALL SELECT 'mes_equipment', 'MES 设备员', '车间主数据只读 + 生产工单只读', 212 UNION ALL SELECT 'mes_team_lead', 'MES 班组长', '车间主数据只读 + 生产工单查询/新增/编辑', 213 ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 4. 授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 1100 AND 1103 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'mes_manager', 'mes_planner')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id = 1100 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_member', 'mes_operator', 'mes_quality', 'mes_equipment', 'mes_team_lead')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 207 AND 210 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_planner')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id BETWEEN 207 AND 209 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_operator', 'mes_team_lead')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id = 207 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('mes_quality', 'mes_equipment')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 5. 菜单 240–241（租户 1 平台菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 240, 1, 0, '基础主数据', '/mes/master-data', 'mes/master-data', 'apartment', 240, 'MENU', 'sys:mes:master:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 240);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 241, 1, 0, '生产工单', '/mes/work-order', 'mes/work-order', 'profile', 241, 'MENU', 'sys:mes:workorder:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 241);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1100 AND 1103;
--   SELECT id, role_code, role_name FROM sys_role WHERE id BETWEEN 20703 AND 20707;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id BETWEEN 240 AND 241;
--   SELECT r.role_code, COUNT(*) FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id WHERE rp.permission_id BETWEEN 1100 AND 1103
--     GROUP BY r.role_code ORDER BY r.role_code;
--   -- 期望：mes_manager=4, mes_planner=4, tenant_admin=4, mes_member/mes_operator/
--   --       mes_quality/mes_equipment/mes_team_lead=1
-- =====================================================================
