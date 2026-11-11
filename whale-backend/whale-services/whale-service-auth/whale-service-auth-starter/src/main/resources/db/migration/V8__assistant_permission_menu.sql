-- =============================================================================
-- 文件        : V8__assistant_permission_menu.sql
-- 说明        : 修正 6.01 手工脚本撞号 —— AI 助手（whale-assistant）权限与菜单从未进库
--
-- 事故复盘（2026-11-11 实测）：
--   db/migration-6.01-assistant.sql 用固定 id 2300/2301 插入
--   sys:assistant:define / sys:assistant:chat，判重条件写成
--   `WHERE NOT EXISTS (... id = 2300 OR perm_code = 'sys:assistant:define')`。
--   但 2300/2301 早已被 migration-5.83-crm-m6-analytics.sql 占用
--   （sys:crm:analytics:dashboard / funnel），于是整条 INSERT 被静默跳过：
--     · 库里只有 CRM 的两条，sys:assistant:* 一条都没有；
--     · 后端 AsstDefinitionController 全部标注 @RequirePermission("sys:assistant:*")
--       → 非超管用户一律 403，且不报配置错误；
--     · 菜单 510/511 也因此没有落库，助手入口不出现。
--   更隐蔽的坑：该脚本的授权段用 `JOIN sys_permission p ON p.id IN (2300, 2301)`，
--   若脚本在 CRM 之后执行，会把 **CRM 分析权限**授予 tenant_admin（幸好本环境该脚本
--   整段未执行，未产生越权数据）。因此本脚本的授权段一律按 perm_code 联查。
--
-- 本脚本（幂等，判重键不用固定 id）：
--   1. 号段 2330–2331 插入两条权限，判重键 = perm_code；
--   2. 按 perm_code 授予 tenant_admin；
--   3. 菜单按 (tenant_id, path) 判重、主键 MD5 派生；
--   4. 复制到已有业务租户。
--
-- 号段登记：2330–2339 = whale-assistant（2330 define / 2331 chat）。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 权限（判重键：perm_code —— 主键撞号不应再让整条语句静默跳过）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2330, 0, 'sys:assistant:define', 'AI 助手管理', 'API', '/assistant/definitions/**', NULL, 100, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:assistant:define');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2331, 0, 'sys:assistant:chat', 'AI 助手问答', 'API', '/assistant/sessions/**', NULL, 101, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:assistant:chat');

-- ---------------------------------------------------------------------
-- 2. 授予 tenant_admin（按 perm_code 联查，绝不按 id —— 按 id 会授错权限）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code IN ('sys:assistant:define', 'sys:assistant:chat')
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- ---------------------------------------------------------------------
-- 3. 平台租户菜单（判重键：tenant_id + path，主键 MD5 派生）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:assistant-definition'), 1, 15), 16, 10) AS UNSIGNED),
       1, 0, '助手管理', '/assistant/definition', 'assistant/definition/index', 'robot', 60,
       'MENU', 'sys:assistant:define', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/assistant/definition' AND deleted = 0
);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:assistant-usage'), 1, 15), 16, 10) AS UNSIGNED),
       1, 0, '助手用量', '/assistant/usage', 'assistant/usage/index', 'bar-chart', 61,
       'MENU', 'sys:assistant:define', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/assistant/usage' AND deleted = 0
);

-- ---------------------------------------------------------------------
-- 4. 复制到已有业务租户（按路径判重）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:', m.path)), 1, 15), 16, 10) AS UNSIGNED),
       t.id, 0, m.name, m.path, m.component, m.icon, m.sort,
       m.menu_type, m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.path IN ('/assistant/definition', '/assistant/usage')
     AND m.deleted = 0
WHERE t.id <> 1 AND t.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = m.path AND x.deleted = 0
  );
