-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）权限增量：M5 营销管理
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M5）
-- 号段     : 权限 1891–1898（CRM 1800–1899）；菜单 437–440（CRM 410–449）
-- 规则     : 全部 INSERT ... WHERE NOT EXISTS，可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（1891–1898）
--    1891 覆盖 GET /crm/campaign/page、/crm/campaign/{id}、/crm/nurture/page、
--         /crm/marketing-send/page、/crm/marketing-send/{id}
--    1892 覆盖 POST/PUT /crm/campaign、/crm/campaign/{id}、/crm/campaign/{id}/roi
--    1893 覆盖 POST /crm/campaign/{id}/audience/preview、/crm/campaign/{id}/audience/lock
--    1894 覆盖 POST /crm/campaign/{id}/status
--    1895 覆盖 POST/PUT /crm/nurture、/crm/nurture/{id}、/crm/nurture/{id}/status
--    1896 覆盖 POST /crm/marketing-send、/crm/marketing-send/{id}/execute
--    1897 覆盖 GET/POST/PUT /crm/partner/page、/crm/partner、/crm/partner/{id}
--    1898 覆盖 GET/POST /crm/partner/report/page、/crm/partner/report、
--         /crm/partner/report/{id}/resolve
--    sys_permission 一行仅登记代表路径，其余端点复用同一权限码。
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1891, 0, 'sys:crm:campaign:list', '查询营销活动与触达', 'API', '/crm/campaign/page', 'GET', 1891, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1891 OR perm_code = 'sys:crm:campaign:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1892, 0, 'sys:crm:campaign:manage', '新建与编辑营销活动', 'API', '/crm/campaign', 'POST', 1892, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1892 OR perm_code = 'sys:crm:campaign:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1893, 0, 'sys:crm:campaign:audience', '活动人群预览与锁定', 'API', '/crm/campaign/{id}/audience/preview', 'POST', 1893, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1893 OR perm_code = 'sys:crm:campaign:audience');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1894, 0, 'sys:crm:campaign:status', '活动状态与 ROI 回填', 'API', '/crm/campaign/{id}/status', 'POST', 1894, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1894 OR perm_code = 'sys:crm:campaign:status');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1895, 0, 'sys:crm:nurture:manage', '培育任务维护', 'API', '/crm/nurture', 'POST', 1895, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1895 OR perm_code = 'sys:crm:nurture:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1896, 0, 'sys:crm:marketing:send', '批量触达创建与执行', 'API', '/crm/marketing-send', 'POST', 1896, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1896 OR perm_code = 'sys:crm:marketing:send');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1897, 0, 'sys:crm:partner:manage', '渠道伙伴维护', 'API', '/crm/partner/page', 'GET', 1897, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1897 OR perm_code = 'sys:crm:partner:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1898, 0, 'sys:crm:partner:report', '渠道报单与冲突裁决', 'API', '/crm/partner/report/page', 'GET', 1898, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1898 OR perm_code = 'sys:crm:partner:report');

-- ---------------------------------------------------------------------
-- 2. 菜单（437 营销活动 / 438 培育任务 / 439 批量触达 / 440 渠道伙伴）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 437, 1, 413, '营销活动', '/crm/campaign', 'crm/campaign/index', 'trophy', 26, 'MENU',
       'sys:crm:campaign:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 437);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 438, 1, 413, '培育任务', '/crm/nurture', 'crm/nurture/index', 'sprout', 27, 'MENU',
       'sys:crm:nurture:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 438);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 439, 1, 413, '批量触达', '/crm/marketing-send', 'crm/marketing-send/index', 'send', 28, 'MENU',
       'sys:crm:marketing:send', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 439);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 440, 1, 413, '渠道伙伴', '/crm/partner', 'crm/partner/index', 'team', 29, 'MENU',
       'sys:crm:partner:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 440);

-- ---------------------------------------------------------------------
-- 3. 授权
--    tenant_admin / crm_admin / crm_marketing / crm_sales_manager：1891–1898
--    crm_sales：1891、1893、1895
--    crm_analyst：1891、1898
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id BETWEEN 1891 AND 1898
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('tenant_admin', 'crm_admin', 'crm_marketing', 'crm_sales_manager')
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
    JOIN sys_permission p ON p.id IN (1891, 1893, 1895)
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
    JOIN sys_permission p ON p.id IN (1891, 1898)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code = 'crm_analyst'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
