-- =====================================================================
-- Wave 5.80 M0-B：PDM 图文档 ACL 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.80-pdm-acl.sql
-- 幂等说明 : 权限登记与角色授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除任何既有数据。
-- 背景     : whale-pdm 图文档中心启用 ACL（目录继承 + 显式 DENY 优先），
--            新增 ACL 查询与维护接口。
-- 权限编号 : PDM 段 900–1099；本脚本使用 910–911。
-- 授权口径 : tenant_admin / pdm_manager / pdm_doc_manager 获得 910–911；
--            pdm_member 不授予。
-- 菜单     : 不新增（ACL 为页内弹窗）。
-- 前置条件 : bluewhale_pdm 已执行 V2__document_center.sql 与
--            V3__document_acl_subject.sql。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 910–911
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 910, 0, 'sys:pdm:doc:acl:list', '查询图文档权限', 'API', '/pdm/acl', 'GET', 910, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 910 OR perm_code = 'sys:pdm:doc:acl:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 911, 0, 'sys:pdm:doc:acl:manage', '维护图文档权限', 'API', '/pdm/acl,/pdm/acl/{id}', 'PUT/DELETE', 911, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 911 OR perm_code = 'sys:pdm:doc:acl:manage');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色编码匹配，覆盖平台与既有业务租户）
--    授权 ID = MD5(role_id + ':' + permission_id) 前 15 位，稳定且幂等；
--    uk_role_permission(role_id, permission_id) 兜底重复插入。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin', 'pdm_manager', 'pdm_doc_manager') AND p.id BETWEEN 910 AND 911) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 权限点登记
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 910 AND 911 ORDER BY id;
--   -- 期望：2 行，910–911。
--   -- 2) 平台模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('pdm_manager','pdm_doc_manager')
--   GROUP BY r.role_code;
--   -- 期望：pdm_manager=12、pdm_doc_manager=12（既有 900–909 + 新增 910–911）。
-- =====================================================================
