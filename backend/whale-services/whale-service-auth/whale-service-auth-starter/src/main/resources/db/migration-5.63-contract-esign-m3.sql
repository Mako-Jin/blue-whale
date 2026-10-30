-- =====================================================================
-- Wave 5.61 M3：合同生效与证据中心权限、菜单迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.63-contract-esign-m3.sql
-- 幂等说明 : NOT EXISTS 幂等插入，可重复执行，不修改/删除任何既有数据。
-- 变更内容 : 权限点 232（合同生效）+ 菜单 31（证据中心，复用 230 查询权限）。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；migration-5.65 已将 232
--            授予 contract_manager / contract_member 及既有租户管理员。
-- =====================================================================

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 232, 0, 'sys:contract:effective', '合同生效', 'API', '/contract/{id}/effective', 'POST', 232, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 232 OR perm_code = 'sys:contract:effective');

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 31, 1, 0, '证据中心', '/contract/evidence', 'contract/evidence', 'file-protect', 31, 'MENU', 'sys:contract:evidence:read', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 31);
