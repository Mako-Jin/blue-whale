-- =====================================================================
-- Wave 5.73：whale-config 业务配置服务权限与菜单（bluewhale_auth）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.73-config-service.sql
-- 幂等说明 : 权限、授权与菜单均为 INSERT IGNORE，可重复执行；不修改/删除既有数据。
-- 背景     : F-05 解冻，whale-config 定位为业务配置管理服务（配置项 CRUD / 版本
--            历史 / 回滚 / 租户隔离），与 Nacos（基础设施配置）并存；本脚本补齐
--            sys:config:* 权限（246–250）、tenant_admin 授权与菜单入口（菜单 35）。
-- 权限编号 : 246 已用到 245（message M1 239–243、pay 244–245），本脚本使用 246–250。
-- 菜单编号 : 已用到 34（消息模板 33 / 支付单 34），本脚本从 35 起分配。
-- 可见性   : /auth/me 按用户权限码过滤菜单（menu.permission_code 命中才返回），
--            无需额外的菜单-角色授权表；tenant_admin 由下方授权语句获得权限。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (246, 0, 'sys:config:list',     '配置项查询', 'API', '/config/item/page',            'GET',    246, 1),
    (247, 0, 'sys:config:add',      '配置项新增', 'API', '/config/item',                 'POST',   247, 1),
    (248, 0, 'sys:config:edit',     '配置项编辑', 'API', '/config/item/{id}',            'PUT',    248, 1),
    (249, 0, 'sys:config:delete',   '配置项删除', 'API', '/config/item/{id}',            'DELETE', 249, 1),
    (250, 0, 'sys:config:rollback', '配置项回滚', 'API', '/config/item/{id}/rollback',   'POST',   250, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED),
       r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.id IN (246, 247, 248, 249, 250)
   AND p.deleted = 0
   AND p.status = 1
WHERE r.deleted = 0
  AND r.status = 1
  AND r.role_code IN ('tenant_admin');

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (35, 1, 0, '业务配置管理', '/config/item', 'config/item', 'setting', 35, 'MENU',
     'sys:config:list', 1, 1);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 246 AND 250;
--   SELECT id, name, path, component, permission_code FROM sys_menu WHERE id = 35;
--   -- 以 admin 登录后 GET /auth/me，确认 menus 树包含「业务配置管理」
-- =====================================================================
