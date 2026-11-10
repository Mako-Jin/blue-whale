-- =====================================================================
-- Wave 5.92 M1-2：地图围栏权限（whale-map）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.92-map-fence.sql
-- 幂等说明 : 权限与授权均 NOT EXISTS 插入，重复执行无副作用。
-- 权限编号 : 2114–2115（承接 5.91 的 2110–2113）
-- 菜单     : 本批仍不建。围栏的后端能力先可用，前端管理页在 whale-map 共享包
--            落地时一起补，避免先挂一个点进去 404 的菜单。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点
-- ---------------------------------------------------------------------
-- 围栏管理（写）：新建/覆盖/删除。围栏直接决定「能不能打卡」，属于配置类高危写权限
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2114, 0, 'sys:map:fence:manage', '管理围栏', 'API',
       '/map/fence,/map/fence/{id}/remove', 'POST', 2114, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2114 OR perm_code = 'sys:map:fence:manage');

-- 围栏查询与判定（读）：列表、详情、点位是否在围栏内、反查命中围栏
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2115, 0, 'sys:map:fence:query', '查询围栏与范围判定', 'API',
       '/map/fence/page,/map/fence/{id},/map/fence/{id}/contains,/map/fence/match', 'GET/POST', 2115, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2115 OR perm_code = 'sys:map:fence:query');

-- ---------------------------------------------------------------------
-- 2. 租户管理员授权
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
             r.id AS role_id, p.id AS permission_id
      FROM sys_role r
      JOIN sys_permission p ON p.id IN (2114, 2115) AND p.deleted = 0 AND p.status = 1
      WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt
                  WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id BETWEEN 2114 AND 2115;
--   SELECT r.tenant_id, COUNT(*) AS granted FROM sys_role r
--     JOIN sys_role_permission rp ON rp.role_id = r.id
--     WHERE rp.permission_id BETWEEN 2114 AND 2115 AND r.deleted = 0 GROUP BY r.tenant_id;
-- =====================================================================
