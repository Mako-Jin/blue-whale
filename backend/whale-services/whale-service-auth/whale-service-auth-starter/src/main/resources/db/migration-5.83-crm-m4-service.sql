-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M4 售后服务（7f-4）
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M4 / 7f-4）
-- 号段     : 权限 1881–1890（CRM 1800–1899）；菜单 432–436（CRM 410–449）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1881–1890）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1881, 0, 'sys:crm:ticket:list', '查询工单', 'API', '/crm/ticket/page', 'GET', 1881, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1881 OR perm_code = 'sys:crm:ticket:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1882, 0, 'sys:crm:ticket:create', '新建工单', 'API', '/crm/ticket', 'POST', 1882, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1882 OR perm_code = 'sys:crm:ticket:create');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1883, 0, 'sys:crm:ticket:assign', '工单分派', 'API', '/crm/ticket/{id}/assign', 'POST', 1883, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1883 OR perm_code = 'sys:crm:ticket:assign');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1884, 0, 'sys:crm:ticket:handle', '工单处理', 'API', '/crm/ticket/{id}/process', 'POST', 1884, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1884 OR perm_code = 'sys:crm:ticket:handle');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1885, 0, 'sys:crm:service:sla:manage', 'SLA 策略与日历维护', 'API', '/crm/service/sla', 'PUT', 1885, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1885 OR perm_code = 'sys:crm:service:sla:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1886, 0, 'sys:crm:kb:list', '查询知识引用', 'API', '/crm/kb/page', 'GET', 1886, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1886 OR perm_code = 'sys:crm:kb:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1887, 0, 'sys:crm:kb:manage', '维护知识引用', 'API', '/crm/kb', 'POST', 1887, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1887 OR perm_code = 'sys:crm:kb:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1888, 0, 'sys:crm:survey:manage', '满意度问卷与结果', 'API', '/crm/satisfaction/page', 'GET', 1888, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1888 OR perm_code = 'sys:crm:survey:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1889, 0, 'sys:crm:return:list', '查询退换货申请', 'API', '/crm/return/page', 'GET', 1889, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1889 OR perm_code = 'sys:crm:return:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1890, 0, 'sys:crm:return:manage', '维护退换货申请', 'API', '/crm/return', 'POST', 1890, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1890 OR perm_code = 'sys:crm:return:manage');

-- ---------------------------------------------------------------------
-- 2. 菜单（432 工单 / 433 SLA 设置 / 434 知识库 / 435 满意度 / 436 退换货）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 432, 1, 413, '工单', '/crm/ticket', 'crm/ticket/index', 'customer-service', 21, 'MENU',
       'sys:crm:ticket:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 432);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 433, 1, 413, 'SLA 设置', '/crm/service-sla', 'crm/service-sla/index', 'clock-circle', 22, 'MENU',
       'sys:crm:service:sla:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 433);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 434, 1, 413, '知识库', '/crm/kb', 'crm/kb/index', 'book', 23, 'MENU',
       'sys:crm:kb:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 434);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 435, 1, 413, '满意度', '/crm/satisfaction', 'crm/satisfaction/index', 'smile', 24, 'MENU',
       'sys:crm:survey:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 435);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 436, 1, 413, '退换货', '/crm/return', 'crm/return/index', 'swap', 25, 'MENU',
       'sys:crm:return:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 436);

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / crm_admin / crm_sales_manager / crm_service：1881–1890
--    crm_sales：1881–1884、1886、1889（工单全流程、知识查询、退货查询）
--    crm_analyst：1881、1886、1888、1889（各查询与满意度）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1881 AND 1890
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('tenant_admin', 'crm_admin', 'crm_sales_manager', 'crm_service')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id IN (1881, 1882, 1883, 1884, 1886, 1889)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code = 'crm_sales'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id IN (1881, 1886, 1888, 1889)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code = 'crm_analyst'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
