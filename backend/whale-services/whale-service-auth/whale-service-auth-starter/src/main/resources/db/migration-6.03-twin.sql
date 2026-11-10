-- =====================================================================
-- Wave 6.03 — 数字孪生平台（whale-twin）权限码注册
-- 目标库：bluewhale_auth（手工增量脚本，幂等，可重复执行）
--
-- 内容：
--   sys:twin:scene（2313）与 tenant_admin 授权。
--   场景与实体共用一个权限码：实体接口挂在 /twin/scenes/{sceneId}/entities
--   下，属同一管理面，拆两个码没有收益。
--
-- 菜单说明：
--   孪生管理面是 qiankun 子应用 whale-twin（8122），入口由基座静态菜单
--   frontend/platform-apps/whale-platform/src/router/menu.ts 承载，**不写 sys_menu**，
--   避免 admin 动态路由指向不存在的页面组件。sys_menu 仅供 admin 管理端使用。
--
-- 验证 SQL：
--   SELECT id, perm_code, path FROM sys_permission WHERE id = 2313;
-- =====================================================================

-- 1) 权限码 -------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2313, 0, 'sys:twin:scene', '数字孪生场景管理', 'API', '/twin/scenes/**', NULL, 113, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2313 OR perm_code = 'sys:twin:scene');

-- 2) tenant_admin 授权 --------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.id = 2313 AND p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id);
