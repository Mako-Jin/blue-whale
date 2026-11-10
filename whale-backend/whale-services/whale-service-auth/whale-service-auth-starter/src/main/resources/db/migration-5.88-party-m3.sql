-- =====================================================================
-- Wave 5.88 M-P3：党政工会「工会」权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.88-party-m3.sql
-- 幂等说明 : 权限、授权、菜单均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 权限编号 : 1970–1980（M-P0 占 1950–1953，M-P1 占 1954–1958，M-P2 占 1959–1961）
--            1970 union:browse / 1971 member:list / 1972 member:manage
--            1973 relief:list / 1974 relief:manage
--            1975 welfare:list / 1976 welfare:manage
--            1977 activity:list / 1978 activity:manage
--            1979 proposal:list / 1980 proposal:manage
-- 菜单编号 : 481 工会会员 / 482 慰问帮扶 / 483 节日福利 / 484 工会活动 / 485 职代会提案（挂在 470 工作台之下）
-- 授权口径 : 工会管理员（union_admin）与租户管理员拿 1970–1980 全部；
--            党务管理员（party_admin）只拿 1970 浏览（可看工会园地，不参与工会资金业务）；
--            普通账号的自助动作（报名/提案/签收）由 1970 承载，归属校验在服务层二次拦截。
-- 前置条件 : migration-5.88-party-m0.sql / m1.sql / m2.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1970–1980
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1970, 0, 'sys:union:browse', '工会园地浏览与自助（活动报名/提案提交/福利签收）', 'API', '/party/union/**', 'GET', 1970, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1970 OR perm_code = 'sys:union:browse');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1971, 0, 'sys:union:member:list', '查看工会会员台账', 'API', '/party/union/member,/party/union/member/**', 'GET', 1971, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1971 OR perm_code = 'sys:union:member:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1972, 0, 'sys:union:member:manage', '工会会员维护（入会/编辑/退会/会费）', 'API', '/party/union/member,/party/union/member/**', 'POST', 1972, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1972 OR perm_code = 'sys:union:member:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1973, 0, 'sys:union:relief:list', '查看慰问与困难帮扶', 'API', '/party/union/relief,/party/union/relief/**', 'GET', 1973, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1973 OR perm_code = 'sys:union:relief:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1974, 0, 'sys:union:relief:manage', '慰问帮扶维护（申请/提交/同步/发放）', 'API', '/party/union/relief,/party/union/relief/**', 'POST', 1974, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1974 OR perm_code = 'sys:union:relief:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1975, 0, 'sys:union:welfare:list', '查看节日福利批次与名单', 'API', '/party/union/welfare,/party/union/welfare/**', 'GET', 1975, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1975 OR perm_code = 'sys:union:welfare:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1976, 0, 'sys:union:welfare:manage', '节日福利维护（新建/编辑/发放/关闭）', 'API', '/party/union/welfare,/party/union/welfare/**', 'POST', 1976, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1976 OR perm_code = 'sys:union:welfare:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1977, 0, 'sys:union:activity:list', '查看工会活动与报名名单', 'API', '/party/union/activity,/party/union/activity/**', 'GET', 1977, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1977 OR perm_code = 'sys:union:activity:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1978, 0, 'sys:union:activity:manage', '工会活动维护（新建/编辑/发布/关闭/取消）', 'API', '/party/union/activity,/party/union/activity/**', 'POST', 1978, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1978 OR perm_code = 'sys:union:activity:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1979, 0, 'sys:union:proposal:list', '查看职代会提案', 'API', '/party/union/proposal,/party/union/proposal/**', 'GET', 1979, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1979 OR perm_code = 'sys:union:proposal:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1980, 0, 'sys:union:proposal:manage', '提案办理（立案/办理/反馈）', 'API', '/party/union/proposal,/party/union/proposal/**', 'POST', 1980, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1980 OR perm_code = 'sys:union:proposal:manage');

-- ---------------------------------------------------------------------
-- 2. 授权
-- ---------------------------------------------------------------------
-- 2.1 工会管理员与租户管理员：1970–1980 全部
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('union_admin', 'tenant_admin') AND p.id BETWEEN 1970 AND 1980 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 2.2 党务管理员：仅浏览（不参与工会资金业务）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('party_admin') AND p.id = 1970 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 481–485（挂在党政工会工作台 470 之下）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 481, 1, 470, '工会会员', '/party/union/member', 'party/union-member', 'team', 481, 'MENU', 'sys:union:member:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 481);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 482, 1, 470, '慰问帮扶', '/party/union/relief', 'party/union-relief', 'heart', 482, 'MENU', 'sys:union:relief:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 482);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 483, 1, 470, '节日福利', '/party/union/welfare', 'party/union-welfare', 'gift', 483, 'MENU', 'sys:union:welfare:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 483);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 484, 1, 470, '工会活动', '/party/union/activity', 'party/union-activity', 'calendar', 484, 'MENU', 'sys:union:activity:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 484);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 485, 1, 470, '职代会提案', '/party/union/proposal', 'party/union-proposal', 'bulb', 485, 'MENU', 'sys:union:proposal:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 485);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path, method FROM sys_permission WHERE id BETWEEN 1970 AND 1980 ORDER BY id;
--   SELECT id, name, path FROM sys_menu WHERE id BETWEEN 481 AND 485 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('union_admin','party_admin','tenant_admin')
--     AND rp.permission_id BETWEEN 1970 AND 1980
--   GROUP BY r.role_code;
--   -- 期望：union_admin / tenant_admin 各 11 项（1970–1980），party_admin 1 项（1970）。
-- =====================================================================
