-- =====================================================================
-- Wave 6.02 — IoT 设备联网平台（whale-iot）权限码注册
-- 目标库：bluewhale_auth（手工增量脚本，幂等，可重复执行）
--
-- 内容：
--   sys:iot:product / sys:iot:device / sys:iot:rule（2310–2312）与 tenant_admin 授权。
--
-- 菜单说明：
--   IoT 管理面是 qiankun 子应用 whale-iot（8121），入口由基座静态菜单
--   frontend/platform-apps/whale-platform/src/router/menu.ts 承载，**不写 sys_menu**，
--   避免 admin 动态路由指向不存在的页面组件。sys_menu 仅供 admin 管理端使用。
--
-- 验证 SQL：
--   SELECT id, perm_code, path FROM sys_permission WHERE id BETWEEN 2310 AND 2312;
-- =====================================================================

-- 1) 权限码 -------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2310, 0, 'sys:iot:product', 'IoT 产品与物模型', 'API', '/iot/products/**', NULL, 110, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2310 OR perm_code = 'sys:iot:product');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2311, 0, 'sys:iot:device', 'IoT 设备管理', 'API', '/iot/devices/**', NULL, 111, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2311 OR perm_code = 'sys:iot:device');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2312, 0, 'sys:iot:rule', 'IoT 规则引擎', 'API', '/iot/rules/**', NULL, 112, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2312 OR perm_code = 'sys:iot:rule');

-- 2) tenant_admin 授权 --------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id IN (2310, 2311, 2312) AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id);
