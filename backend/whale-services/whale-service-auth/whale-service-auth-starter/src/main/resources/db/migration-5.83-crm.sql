-- =====================================================================
-- Wave 5.83 M0 + M1：客户关系应用（whale-crm）权限与角色增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.83-crm.sql
-- 幂等说明 : 权限、角色与授权均为 NOT EXISTS 幂等插入，可重复执行；
--            不修改 / 删除既有数据。
-- 权限编号 : 存量 337–354 保留不动；本脚本使用 1800 与 1810–1830。
-- 菜单编号 : 413–420 预留给 M1 客户页面；前端未交付前不登记菜单，
--            避免出现「点击即 403」的空入口。
-- 角色编号 : 25128 crm_sales / 25129 crm_sales_manager / 25130 crm_service
--            / 25131 crm_marketing / 25132 crm_analyst。
-- 授权口径 : crm_admin / tenant_admin / crm_sales_manager：1800 + 1810–1830；
--            crm_sales：客户维护、联系人、地址、画像、跟进、公海领取释放；
--            crm_service / crm_marketing / crm_analyst：只读 + 跟进 + 画像。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台权限点（1800 敏感明文 + 1810–1830 客户主数据）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1800, 0, 'sys:crm:sensitive:view', '查看客户敏感明文', 'API', '/crm/customer/page,/crm/customer/{id}', 'GET', 1800, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1800 OR perm_code = 'sys:crm:sensitive:view');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1810, 0, 'sys:crm:customer:list', '查询客户列表与 360', 'API', '/crm/customer/page,/crm/customer/{id},/crm/customer/{id}/360', 'GET', 1810, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1810 OR perm_code = 'sys:crm:customer:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1811, 0, 'sys:crm:customer:create', '新建客户', 'API', '/crm/customer', 'POST', 1811, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1811 OR perm_code = 'sys:crm:customer:create');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1812, 0, 'sys:crm:customer:update', '编辑客户', 'API', '/crm/customer/{id}', 'PUT', 1812, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1812 OR perm_code = 'sys:crm:customer:update');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1813, 0, 'sys:crm:customer:status', '变更客户状态', 'API', '/crm/customer/{id}/status', 'PUT', 1813, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1813 OR perm_code = 'sys:crm:customer:status');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1814, 0, 'sys:crm:customer:level', '变更客户等级', 'API', '/crm/customer/{id}/level', 'PUT', 1814, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1814 OR perm_code = 'sys:crm:customer:level');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1815, 0, 'sys:crm:customer:duplicate', '客户查重', 'API', '/crm/customer/duplicate-check', 'GET', 1815, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1815 OR perm_code = 'sys:crm:customer:duplicate');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1816, 0, 'sys:crm:contact:list', '查询联系人', 'API', '/crm/customer/{customerId}/contact', 'GET', 1816, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1816 OR perm_code = 'sys:crm:contact:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1817, 0, 'sys:crm:contact:manage', '管理联系人', 'API', '/crm/customer/{customerId}/contact,/crm/customer/contact/{contactId}', 'POST,PUT,DELETE', 1817, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1817 OR perm_code = 'sys:crm:contact:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1818, 0, 'sys:crm:address:list', '查询客户地址', 'API', '/crm/customer/{customerId}/address', 'GET', 1818, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1818 OR perm_code = 'sys:crm:address:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1819, 0, 'sys:crm:address:manage', '管理客户地址', 'API', '/crm/customer/{customerId}/address,/crm/customer/address/{addressId}', 'POST,PUT,DELETE', 1819, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1819 OR perm_code = 'sys:crm:address:manage');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1820, 0, 'sys:crm:profile:view', '查看客户画像', 'API', '/crm/customer/{id}/profile', 'GET', 1820, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1820 OR perm_code = 'sys:crm:profile:view');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1821, 0, 'sys:crm:profile:save', '保存客户画像', 'API', '/crm/customer/{id}/profile', 'PUT', 1821, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1821 OR perm_code = 'sys:crm:profile:save');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1822, 0, 'sys:crm:follow-up:list', '查询跟进记录', 'API', '/crm/follow-up/page', 'GET', 1822, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1822 OR perm_code = 'sys:crm:follow-up:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1823, 0, 'sys:crm:follow-up:create', '新增跟进记录', 'API', '/crm/follow-up', 'POST', 1823, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1823 OR perm_code = 'sys:crm:follow-up:create');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1824, 0, 'sys:crm:pool:view', '查看公海与负责人历史', 'API', '/crm/pool/page,/crm/pool/{id}/owner-logs', 'GET', 1824, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1824 OR perm_code = 'sys:crm:pool:view');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1825, 0, 'sys:crm:pool:claim', '领取公海客户', 'API', '/crm/pool/{id}/claim', 'POST', 1825, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1825 OR perm_code = 'sys:crm:pool:claim');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1826, 0, 'sys:crm:pool:release', '释放客户到公海', 'API', '/crm/pool/{id}/release', 'POST', 1826, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1826 OR perm_code = 'sys:crm:pool:release');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1827, 0, 'sys:crm:pool:assign', '分配客户负责人', 'API', '/crm/pool/{id}/assign', 'POST', 1827, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1827 OR perm_code = 'sys:crm:pool:assign');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1828, 0, 'sys:crm:pool:rule', '公海规则维护', 'API', '/crm/pool/rule', 'GET,PUT', 1828, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1828 OR perm_code = 'sys:crm:pool:rule');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1829, 0, 'sys:crm:pool:recycle', '手动回收公海客户', 'API', '/crm/pool/recycle', 'POST', 1829, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1829 OR perm_code = 'sys:crm:pool:recycle');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1830, 0, 'sys:crm:customer:delete', '删除客户', 'API', '/crm/customer/{id}', 'DELETE', 1830, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1830 OR perm_code = 'sys:crm:customer:delete');

-- ---------------------------------------------------------------------
-- 2. 平台租户角色模板（25128–25132）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25128, 1, 'crm_sales', 'CRM 销售专员', '客户维护、联系人、地址、画像、跟进与公海领取释放', 260, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25128 OR (tenant_id = 1 AND role_code = 'crm_sales'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25129, 1, 'crm_sales_manager', 'CRM 销售主管', '全量客户数据、分级、状态、分配、公海规则与回收', 261, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25129 OR (tenant_id = 1 AND role_code = 'crm_sales_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25130, 1, 'crm_service', 'CRM 客服专员', '客户与联系人只读、跟进记录、地址与画像查询', 262, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25130 OR (tenant_id = 1 AND role_code = 'crm_service'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25131, 1, 'crm_marketing', 'CRM 市场专员', '客户与画像只读、查重与跟进记录', 263, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25131 OR (tenant_id = 1 AND role_code = 'crm_marketing'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 25132, 1, 'crm_analyst', 'CRM 销售分析', '全量客户数据只读、画像、跟进与公海查询', 264, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 25132 OR (tenant_id = 1 AND role_code = 'crm_analyst'));

-- ---------------------------------------------------------------------
-- 3. 既有业务租户复制角色（仅 tenant_type='TENANT' 的启用租户）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name,
           x.description AS description, x.sort_no AS sort_no, 1 AS status
    FROM sys_tenant t
    CROSS JOIN (
        SELECT 'crm_sales' AS role_code, 'CRM 销售专员' AS role_name,
               '客户维护、联系人、地址、画像、跟进与公海领取释放' AS description, 260 AS sort_no
        UNION ALL SELECT 'crm_sales_manager', 'CRM 销售主管',
               '全量客户数据、分级、状态、分配、公海规则与回收', 261
        UNION ALL SELECT 'crm_service', 'CRM 客服专员',
               '客户与联系人只读、跟进记录、地址与画像查询', 262
        UNION ALL SELECT 'crm_marketing', 'CRM 市场专员', '客户与画像只读、查重与跟进记录', 263
        UNION ALL SELECT 'crm_analyst', 'CRM 销售分析', '全量客户数据只读、画像、跟进与公海查询', 264
    ) x
    WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role tgt
    WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code)
);

-- ---------------------------------------------------------------------
-- 4. 授权
--    crm_admin / tenant_admin / crm_sales_manager：1800 + 1810–1830
--    crm_sales：1810–1812、1814–1826
--    crm_service：1810、1816、1818、1820、1822、1823
--    crm_marketing：1810、1815、1820、1822
--    crm_analyst：1810、1820、1822、1824
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON (p.id = 1800 OR p.id BETWEEN 1810 AND 1830)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1
      AND r.role_code IN ('crm_admin', 'tenant_admin', 'crm_sales_manager')
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
    JOIN sys_permission p ON p.id IN (1810, 1811, 1812, 1814, 1815, 1816, 1817, 1818, 1819, 1820, 1821, 1822, 1823, 1824, 1825, 1826)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'crm_sales'
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
    JOIN sys_permission p ON p.id IN (1810, 1816, 1818, 1820, 1822, 1823)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'crm_service'
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
    JOIN sys_permission p ON p.id IN (1810, 1815, 1820, 1822)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'crm_marketing'
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
    JOIN sys_permission p ON p.id IN (1810, 1820, 1822, 1824)
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'crm_analyst'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
