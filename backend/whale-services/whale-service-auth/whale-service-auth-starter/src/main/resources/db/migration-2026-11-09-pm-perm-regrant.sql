-- =====================================================================
-- 权限号段撞号修复（2026-11-09）：whale-pm 172/173 被租户密码策略占用
-- =====================================================================
-- 适用库   : bluewhale_auth（权限中心）
-- 现象     : `sys:pm:project:list`（查询项目）/ `sys:pm:project:add`（新增项目）
--            在库里**不存在**，而 `172/173` 是 `sys:tenant:pwdpolicy:list/edit`。
--            5.57 / 5.66 的授权按「172–202 区间」发放，于是
--            pm_manager 拿到了「租户密码策略查看 + 编辑」，pm_member 拿到了查看权，
--            同时两个角色都看不到项目列表（接口 403）。
-- 根因     : 172/173 被 `V3__tenant_password_policy.sql` 与 `migration-5.55-pm.sql` 同时声明。
--            幂等条件 `WHERE id = ? OR perm_code = ?` 让后执行者整条跳过，脚本却返回成功。
-- 修法     : PM 的两项改为 2320/2321（PM 续段），并把误授给 PM 角色的密码策略授权撤销。
-- 幂等性   : DELETE 天然幂等；INSERT 全部 NOT EXISTS，可重复执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 撤销误授：PM 角色不应持有租户密码策略
-- ---------------------------------------------------------------------
DELETE rp FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
WHERE r.role_code IN ('pm_manager', 'pm_member')
  AND rp.permission_id IN (172, 173);

-- ---------------------------------------------------------------------
-- 2. 补建 PM 的两项权限（2320 / 2321）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2320, 0, 'sys:pm:project:list', '查询项目', 'API', '/pm/project/page,/pm/project/{id}', 'GET', 2320, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2320 OR perm_code = 'sys:pm:project:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2321, 0, 'sys:pm:project:add', '新增项目', 'API', '/pm/project', 'POST', 2321, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2321 OR perm_code = 'sys:pm:project:add');

-- ---------------------------------------------------------------------
-- 3. 按 perm_code 重新授权（不依赖 ID，避免再次被 ID 变动吞掉）
--    pm_manager：两项全给；pm_member：只给查询
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p
      ON p.perm_code IN ('sys:pm:project:list', 'sys:pm:project:add')
     AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'pm_manager'
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
    JOIN sys_permission p
      ON p.perm_code = 'sys:pm:project:list'
     AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'pm_member'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- =====================================================================
-- 校验：
--   SELECT id, perm_code FROM sys_permission WHERE id IN (172,173,2320,2321);
--     期望：172/173 = tenant:pwdpolicy；2320/2321 = pm:project
--   SELECT r.role_code, p.perm_code FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE r.role_code IN ('pm_manager','pm_member') AND p.perm_code LIKE 'sys:tenant:pwdpolicy%';
--     期望：0 行
-- =====================================================================
