-- =====================================================================
-- Wave 5.91 M0：地图服务权限（whale-map）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.91-map-m0.sql
-- 幂等说明 : 权限与授权均 NOT EXISTS 插入，重复执行无副作用。
-- 权限编号 : 2110–2113（2110 起为地图段；2100–2109 已被 whale-mail 占用）
-- 菜单     : 本批不建。M0 没有前端页面，建菜单只会得到一个点进去 404 的死链；
--            前端 whale-map 共享包落地时再补菜单，编号顺延 486。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点
-- ---------------------------------------------------------------------
-- 解析与检索（地理编码 / 逆地理 / POI / 路径 / 距离）：都是只读外呼，合成一个权限点，
-- 拆开会让「配权限」变成给业务人员出考题
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2110, 0, 'sys:map:geo:query', '地图地址解析与检索', 'API',
       '/map/geocode,/map/regeo,/map/poi/search,/map/route,/map/distance', 'POST', 2110, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2110 OR perm_code = 'sys:map:geo:query');

-- 打点管理（写）
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2111, 0, 'sys:map:location:manage', '管理业务打点', 'API',
       '/map/location/{bizType}/{bizId},/map/location/{bizType}/{bizId}/remove', 'PUT', 2111, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2111 OR perm_code = 'sys:map:location:manage');

-- 打点查询（读）
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2112, 0, 'sys:map:location:query', '查询业务打点与附近', 'API',
       '/map/location/{bizType}/{bizId},/map/location/nearby', 'GET', 2112, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2112 OR perm_code = 'sys:map:location:query');

-- 供应商健康与配额（运维口径，只给管理员）
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2113, 0, 'sys:map:provider:view', '查看地图供应商状态', 'API',
       '/map/provider/health', 'GET', 2113, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2113 OR perm_code = 'sys:map:provider:view');

-- ---------------------------------------------------------------------
-- 2. 租户管理员授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
             r.id AS role_id, p.id AS permission_id
      FROM sys_role r
      JOIN sys_permission p ON p.id IN (2110, 2111, 2112, 2113) AND p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt
                  WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id BETWEEN 2110 AND 2113;
--   SELECT r.tenant_id, COUNT(*) AS granted FROM sys_role r
--     JOIN sys_role_permission rp ON rp.role_id = r.id
--     WHERE rp.permission_id BETWEEN 2110 AND 2113 AND r.deleted = 0 GROUP BY r.tenant_id;
-- =====================================================================
