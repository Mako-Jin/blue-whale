-- =============================================================================
-- 文件        : V5__agent_menu_grant.sql
-- 说明        : Wave 6.18b —— 知识助手（whale-agent）可用性闭环
--
-- 背景：
--   菜单种子 sys_menu#9（/agent，DIR）与 #10（/agent/chat，MENU，权限码
--   sys:agent:chat）由 Wave 5.47 写入平台租户（tenant_id = 1）。
--   - 新租户开通时由 TenantProvisioningAppService 复制平台菜单模板 + 授予
--     tenant_admin 全部启用权限，因此新租户开箱可用；
--   - 但**存量业务租户**（V5 之前开通的）既没有这两个菜单行，其 tenant_admin
--     也没有 sys:agent:* 权限码 —— 表现为菜单不显示、接口 403，知识助手实际不可用。
--
-- 本脚本（幂等）：
--   1. 为缺 /agent 目录的存量租户复制目录菜单；
--   2. 为缺 /agent/chat 的存量租户复制子菜单（父节点取本租户的 /agent 目录）；
--   3. 给存量租户的 tenant_admin 授予 100/101/102
--      （sys:agent:chat / sys:agent:search / sys:agent:index:rebuild）。
--
-- 主键规则沿用 V3：CAST(CONV(SUBSTR(MD5(...), 1, 15), 16, 10) AS UNSIGNED)，
-- 保证同一 (租户, 菜单) 组合重复执行不会生成第二行。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 目录菜单 /agent（父级为根，tenant_id = 各业务租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:agent')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, 0, m.name, m.path, m.component, m.icon, m.sort,
       m.menu_type, m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.path = '/agent' AND m.menu_type = 'DIR' AND m.deleted = 0
WHERE t.id <> 1 AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = t.id AND x.path = m.path AND x.deleted = 0);

-- ---------------------------------------------------------------------
-- 2. 子菜单 /agent/chat（父级取本租户的 /agent 目录）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:agent-chat')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(dir.id, 0), m.name, m.path, m.component, m.icon, m.sort,
       m.menu_type, m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.path = '/agent/chat' AND m.deleted = 0
LEFT JOIN sys_menu dir ON dir.tenant_id = t.id AND dir.path = '/agent'
     AND dir.menu_type = 'DIR' AND dir.deleted = 0
WHERE t.id <> 1 AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = t.id AND x.path = m.path AND x.deleted = 0);

-- ---------------------------------------------------------------------
-- 3. 授权：存量租户的 tenant_admin 补齐 sys:agent:*（100/101/102）
--    超管由 PermissionInterceptor 直接放行，无需授予。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id IN (100, 101, 102) AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
