-- =====================================================================
-- Wave 5.88 M-P0：党政工会（whale-party）独立服务权限与菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.88-party-m0.sql
-- 幂等说明 : 权限、角色、授权、菜单均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : 党政工会原计划并入 whale-oa（权限 sys:oa:party:* / sys:oa:union:*），
--            2026-11-06 改为独立服务 whale-party（9009 / /party/** / bluewhale_party）。
-- 权限编号 : 1950–1999 号段**沿用**（Wave 5.87 已登记给党政工会，仅是当时挂在 OA 名下），
--            前缀改为 sys:party:* / sys:union:*，让日志与授权清单里「权限属于哪个服务」
--            一眼可读。不换号段是为了不牵动已发布的权限迁移。
--            本脚本占用 1950–1953 基线；M-P1 起从 1954 之后追加。
-- 菜单编号 : 470–489；本脚本使用 470（党政工会工作台）。
-- 角色编号 : 25136–25138（party_admin / branch_secretary / union_admin），
--            与 LMS 25133–25135、CRM 25128–25132、OA 25123–25127 不冲突。
-- 授权口径 : 党务管理员（party_admin）与租户管理员拿全部四项；
--            支部书记（branch_secretary）只拿「看 + 党员台账」——支部是组织单元，
--            书记能维护本支部党员信息，但不该能新建/撤销支部或改支部层级；
--            工会管理员（union_admin）在 M-P0 只拿门禁浏览权，工会权限随 M-P3 追加。
-- 前置条件 : migration-5.87-lms-m0.sql 已执行（菜单 450 段、角色号段已生效）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1950–1953
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1950, 0, 'sys:party:browse', '党政工会浏览', 'API', '/party/meta', 'GET', 1950, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1950 OR perm_code = 'sys:party:browse');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1951, 0, 'sys:party:branch:list', '查看支部与组织架构', 'API', '/party/branch,/party/branch/**', 'GET', 1951, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1951 OR perm_code = 'sys:party:branch:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1952, 0, 'sys:party:branch:manage', '支部维护（新建/换届/停用）', 'API', '/party/branch,/party/branch/**', 'POST', 1952, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1952 OR perm_code = 'sys:party:branch:manage');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1953, 0, 'sys:party:member:manage', '党员台账维护（转入转出/党费基数）', 'API', '/party/member,/party/member/**', 'POST', 1953, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1953 OR perm_code = 'sys:party:member:manage');

-- ---------------------------------------------------------------------
-- 2. 角色 25136–25138（平台租户固定 ID + 业务租户按 MD5 派生）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25136, 1, 'party_admin', '党务管理员', '支部/党员/组织生活/党费/党建内容全部功能', 256, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25136 OR (tenant_id = 1 AND role_code = 'party_admin'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25137, 1, 'branch_secretary', '支部书记', '本支部党员台账维护与组织生活记录', 257, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25137 OR (tenant_id = 1 AND role_code = 'branch_secretary'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25138, 1, 'union_admin', '工会管理员', '会员/慰问/福利/活动/提案管理', 258, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25138 OR (tenant_id = 1 AND role_code = 'union_admin'));

INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':party_admin')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'party_admin' AS role_code, '党务管理员' AS role_name, '支部/党员/组织生活/党费/党建内容全部功能' AS description, 256 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':branch_secretary')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'branch_secretary' AS role_code, '支部书记' AS role_name, '本支部党员台账维护与组织生活记录' AS description, 257 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':union_admin')), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, 'union_admin' AS role_code, '工会管理员' AS role_name, '会员/慰问/福利/活动/提案管理' AS description, 258 AS sort_no, 1 AS status FROM sys_tenant t WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 3. 授权（按角色编码匹配，覆盖平台与既有业务租户）
-- ---------------------------------------------------------------------
-- 3.1 党务管理员与租户管理员：1950–1953 全部
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('party_admin', 'tenant_admin') AND p.id BETWEEN 1950 AND 1953 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.2 支部书记：看 + 党员台账（不含支部结构与层级维护）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('branch_secretary') AND p.id IN (1950, 1951, 1953) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));
-- 3.3 工会管理员：M-P0 仅门禁浏览（工会业务权限随 M-P3 追加到 1970 起）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( r.role_code IN ('union_admin') AND p.id = 1950 )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 4. 菜单 470（党政工会工作台，顶层）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 470, 1, 0, '党政工会', '/party', 'party/index', 'flag', 470, 'MENU', 'sys:party:browse', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 470);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id BETWEEN 1950 AND 1953 ORDER BY id;
--   SELECT id, role_code FROM sys_role WHERE id BETWEEN 25136 AND 25138 ORDER BY id;
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.role_code IN ('party_admin','branch_secretary','union_admin','tenant_admin')
--   GROUP BY r.role_code;
--   -- 期望：party_admin / tenant_admin 各 +4，branch_secretary +3，union_admin +1。
--   SELECT id, name, path FROM sys_menu WHERE id = 470;
-- =====================================================================
