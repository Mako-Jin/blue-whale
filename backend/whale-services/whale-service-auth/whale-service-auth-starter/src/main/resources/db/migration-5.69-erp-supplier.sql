-- =====================================================================
-- Wave 5.69：ERP 供应商主数据（bluewhale_auth）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.69-erp-supplier.sql
-- 幂等说明 : 菜单/权限/授权均为 INSERT IGNORE，可重复执行；不修改/删除既有数据。
-- 背景     : 供应商主数据落在 ERP（不是 CRM）；本迁移只补菜单与权限，
--            业务表 erp_supplier 在 bluewhale_erp 的 init.sql 中创建。
-- 权限     : 235 list / 236 add / 237 edit / 238 delete
-- 授权     : tenant_admin 全量；erp_manager 全量；erp_member 只读 + 新增 + 编辑。
-- =====================================================================

INSERT IGNORE INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (235, 0, 'sys:erp:supplier:list',   '查询供应商', 'API', '/erp/supplier/page,/erp/supplier/{id}', 'GET',    235, 1),
    (236, 0, 'sys:erp:supplier:add',    '新增供应商', 'API', '/erp/supplier',                          'POST',   236, 1),
    (237, 0, 'sys:erp:supplier:edit',   '编辑供应商', 'API', '/erp/supplier/{id}',                     'PUT',    237, 1),
    (238, 0, 'sys:erp:supplier:delete', '删除供应商', 'API', '/erp/supplier/{id}',                     'DELETE', 238, 1);

INSERT IGNORE INTO sys_menu
    (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (32, 1, 0, '供应商管理', '/erp/supplier', 'erp/supplier', 'team', 32, 'MENU', 'sys:erp:supplier:list', 1, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED),
       r.id, p.id
FROM sys_role r
JOIN sys_permission p
    ON p.perm_code IN ('sys:erp:supplier:list', 'sys:erp:supplier:add',
                       'sys:erp:supplier:edit', 'sys:erp:supplier:delete')
   AND p.deleted = 0
   AND p.status = 1
WHERE r.deleted = 0
  AND r.status = 1
  AND (
        r.role_code = 'tenant_admin'
     OR r.role_code = 'erp_manager'
     OR (r.role_code = 'erp_member' AND p.perm_code <> 'sys:erp:supplier:delete')
      );

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 235 AND 238;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id = 32;
--   SELECT r.tenant_id, r.role_code, COUNT(*) AS granted
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE rp.permission_id BETWEEN 235 AND 238 AND r.deleted = 0 AND r.status = 1
--   GROUP BY r.tenant_id, r.role_code;
-- =====================================================================
