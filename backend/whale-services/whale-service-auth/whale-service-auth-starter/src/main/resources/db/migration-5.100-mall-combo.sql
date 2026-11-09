-- =====================================================================
-- Wave 5.100 商城组合计价与履约增强：活动 / 会员 / 权限与菜单
--   权限编号 : 2225 活动列表（运营）/ 2226 活动维护（运营）
--              2227 会员列表（运营）/ 2228 会员维护（运营）
--              2200–2224 已占用（5.94–5.98），本波次顺延。
--   授权口径 : tenant_admin 获得全部四个。活动与会员登记均为纯运营动作，
--              买家侧不新增权限点（会员价与活动价在下单计价中自动生效）。
--   菜单     : 504「活动管理」、505「会员管理」，挂在商城目录 491 下
--              （491–503 已占用）。
--   幂等口径 : 全部 NOT EXISTS，可重放。注意：本目录（db/）下的
--              migration-5.x 为手工重放脚本，不经 Flyway 自动执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2225, 0, 'sys:mall:activity:list', '商城活动列表', 'API',
       '/mall/activity/page,/mall/activity/*', 'GET', 2225, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2225)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:activity:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2226, 0, 'sys:mall:activity:manage', '商城活动维护', 'API',
       '/mall/activity,/mall/activity/*/status', 'POST', 2226, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2226)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:activity:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2227, 0, 'sys:mall:member:list', '商城会员列表', 'API',
       '/mall/member/page,/mall/member/*', 'GET', 2227, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2227)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:member:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2228, 0, 'sys:mall:member:manage', '商城会员维护', 'API',
       '/mall/member,/mall/member/*', 'POST', 2228, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2228)
  AND NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:mall:member:manage');

-- ---------------------------------------------------------------------
-- 2. tenant_admin 授权（四个权限点各一条；MD5 稳定主键，双重防重）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
             r.id AS role_id, p.id AS permission_id
      FROM sys_role r
      JOIN sys_permission p ON p.id IN (2225, 2226, 2227, 2228) AND p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin') src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt
                  WHERE tgt.id = src.id
                     OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单（504 / 505，挂在「商城」目录 491 下）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 504, 1, 491, '活动管理', '/mall/activity', 'mall/activity/index', 'thunderbolt', 11, 'MENU', 'sys:mall:activity:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 504);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 505, 1, 491, '会员管理', '/mall/member', 'mall/member/index', 'team', 12, 'MENU', 'sys:mall:member:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 505);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 2225 AND 2228;
--   SELECT COUNT(*) FROM sys_role_permission WHERE permission_id BETWEEN 2225 AND 2228;
--   SELECT id, name, path FROM sys_menu WHERE id IN (504, 505);
-- =====================================================================
