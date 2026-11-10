-- =====================================================================
-- Wave 5.54 Phase 1：多租户 RBAC 迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 前置条件 : 已执行 migration-5.54-tenant.sql（sys_tenant 与 sys_user.tenant_id 已存在）
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.54-tenant-rbac.sql
-- 幂等说明 : 列/索引/权限点均先检测再变更，可重复执行；不删除任何业务数据。
-- 变更内容 :
--   1. sys_tenant 增加 tenant_type / expire_time / quota_json，并把 id=1 标记为 PLATFORM；
--   2. sys_role / sys_menu / sys_dept 增加 tenant_id（存量数据默认归属租户 1）；
--   3. 角色编码唯一键由 (role_code) 升级为 (tenant_id, role_code)；
--   4. 新增平台侧租户管理权限点 sys:tenant:*（160–164）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 幂等工具：列 / 索引存在性检测
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
DROP PROCEDURE IF EXISTS drop_index_if_exists;

DELIMITER $$

CREATE PROCEDURE add_column_if_missing(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl VARCHAR(512))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) = 0 THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE add_index_if_missing(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl VARCHAR(512))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index) = 0 THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE drop_index_if_exists(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index) > 0 THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' DROP INDEX ', p_index, '');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. 租户生命周期与配额
-- ---------------------------------------------------------------------
CALL add_column_if_missing('sys_tenant', 'tenant_type',
    'ALTER TABLE sys_tenant ADD COLUMN tenant_type VARCHAR(16) NOT NULL DEFAULT ''TENANT'' COMMENT ''租户类型：PLATFORM 平台运营 / TENANT 业务租户'' AFTER tenant_name');
CALL add_column_if_missing('sys_tenant', 'expire_time',
    'ALTER TABLE sys_tenant ADD COLUMN expire_time DATETIME DEFAULT NULL COMMENT ''到期时间，NULL 表示长期有效'' AFTER status');
CALL add_column_if_missing('sys_tenant', 'quota_json',
    'ALTER TABLE sys_tenant ADD COLUMN quota_json VARCHAR(1024) DEFAULT NULL COMMENT ''配额 JSON'' AFTER expire_time');

-- 平台租户（id=1）标记为 PLATFORM：幂等更新，仅修正存量行
UPDATE sys_tenant SET tenant_type = 'PLATFORM' WHERE id = 1 AND tenant_type <> 'PLATFORM';

-- ---------------------------------------------------------------------
-- 2. 角色 / 菜单 / 部门租户化（存量数据默认归属租户 1）
-- ---------------------------------------------------------------------
CALL add_column_if_missing('sys_role', 'tenant_id',
    'ALTER TABLE sys_role ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''所属租户ID（逻辑关联 sys_tenant.id）'' AFTER id');
CALL add_column_if_missing('sys_menu', 'tenant_id',
    'ALTER TABLE sys_menu ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''所属租户ID（逻辑关联 sys_tenant.id）'' AFTER id');
CALL add_column_if_missing('sys_dept', 'tenant_id',
    'ALTER TABLE sys_dept ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''所属租户ID（逻辑关联 sys_tenant.id）'' AFTER id');

CALL add_index_if_missing('sys_role', 'idx_tenant_id',
    'ALTER TABLE sys_role ADD INDEX idx_tenant_id (tenant_id)');
CALL add_index_if_missing('sys_menu', 'idx_tenant_id',
    'ALTER TABLE sys_menu ADD INDEX idx_tenant_id (tenant_id)');
CALL add_index_if_missing('sys_dept', 'idx_tenant_id',
    'ALTER TABLE sys_dept ADD INDEX idx_tenant_id (tenant_id)');

-- ---------------------------------------------------------------------
-- 3. 角色编码唯一键升级：uk_role_code → uk_tenant_role_code
-- ---------------------------------------------------------------------
CALL drop_index_if_exists('sys_role', 'uk_role_code');
CALL add_index_if_missing('sys_role', 'uk_tenant_role_code',
    'ALTER TABLE sys_role ADD UNIQUE KEY uk_tenant_role_code (tenant_id, role_code)');

-- ---------------------------------------------------------------------
-- 4. 平台侧租户管理权限点（全局权限表，NOT EXISTS 幂等插入）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 160, 0, 'sys:tenant:list', '查询租户', 'API', '/tenant/page,/tenant/{id}', 'GET', 160, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 160 OR perm_code = 'sys:tenant:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 161, 0, 'sys:tenant:add', '开通租户', 'API', '/tenant', 'POST', 161, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 161 OR perm_code = 'sys:tenant:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 162, 0, 'sys:tenant:edit', '编辑租户', 'API', '/tenant/{id}', 'PUT', 162, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 162 OR perm_code = 'sys:tenant:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 163, 0, 'sys:tenant:status', '启停租户', 'API', '/tenant/{id}/status', 'PUT', 163, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 163 OR perm_code = 'sys:tenant:status');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 164, 0, 'sys:tenant:renew', '租户续期', 'API', '/tenant/{id}/renew', 'PUT', 164, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 164 OR perm_code = 'sys:tenant:renew');

-- ---------------------------------------------------------------------
-- 5. 清理幂等工具存储过程
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
DROP PROCEDURE IF EXISTS drop_index_if_exists;

-- =====================================================================
-- 完成。校验建议：
--   SHOW CREATE TABLE sys_tenant;   -- 含 tenant_type / expire_time / quota_json
--   SHOW CREATE TABLE sys_role;     -- 含 tenant_id 与 uk_tenant_role_code
--   SELECT * FROM sys_permission WHERE perm_code LIKE 'sys:tenant:%';
-- =====================================================================
