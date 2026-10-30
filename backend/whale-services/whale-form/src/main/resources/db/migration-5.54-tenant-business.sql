-- ============================================================================
-- Wave 5.54 Phase 3：表单元数据租户化（幂等脚本）
-- 适用库   : bluewhale_form
-- 执行方式 : mysql -u<user> -p bluewhale_form < migration-5.54-tenant-business.sql
-- 说明：
--   1. 新增 tenant_id 默认 1，存量数据自动归属默认租户，无需额外回填；
--   2. 业务唯一性由单列索引升级为 (tenant_id, code) 复合索引（逻辑删除仍由服务层前置校验）；
--   3. form_data 与已发布动态物理表在 Phase 0 已具备 tenant_id，不在本脚本范围；
--   4. 升级前无租户归属的日志记录统一回填默认租户 1。
-- ============================================================================

DROP PROCEDURE IF EXISTS whale_add_tenant_column;
DROP PROCEDURE IF EXISTS whale_add_index;
DROP PROCEDURE IF EXISTS whale_drop_index;
DROP PROCEDURE IF EXISTS whale_backfill_log_tenant;

DELIMITER $$

CREATE PROCEDURE whale_add_tenant_column(IN tbl VARCHAR(64))
BEGIN
    IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = 'tenant_id') THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 '
            'COMMENT ''租户ID（行级隔离）'' AFTER id');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE whale_add_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(128))
BEGIN
    IF NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' ADD KEY ', idx, ' (', cols, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE whale_drop_index(IN tbl VARCHAR(64), IN idx VARCHAR(64))
BEGIN
    IF EXISTS(SELECT 1 FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' DROP INDEX ', idx, '');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE whale_backfill_log_tenant(IN tbl VARCHAR(64))
BEGIN
    IF EXISTS(SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = 'tenant_id') THEN
        SET @ddl = CONCAT('UPDATE ', tbl, ' SET tenant_id = 1 WHERE tenant_id IS NULL');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL whale_add_tenant_column('form_definition');
CALL whale_add_tenant_column('form_table_definition');
CALL whale_add_tenant_column('form_column_meta');
CALL whale_add_tenant_column('form_field_config');
CALL whale_add_tenant_column('form_field_option');
CALL whale_add_tenant_column('form_table_registry');
CALL whale_add_tenant_column('form_definition_version');

CALL whale_drop_index('form_definition', 'idx_code');
CALL whale_add_index('form_definition', 'idx_tenant_code', 'tenant_id, code');

CALL whale_drop_index('form_table_definition', 'idx_table_code');
CALL whale_add_index('form_table_definition', 'idx_tenant_table_code', 'tenant_id, table_code');

CALL whale_drop_index('form_table_registry', 'idx_code_version');
CALL whale_drop_index('form_table_registry', 'idx_table_code_version');
CALL whale_add_index('form_table_registry', 'idx_tenant_code_version',
    'tenant_id, definition_code, definition_version');
CALL whale_add_index('form_table_registry', 'idx_tenant_table_code_version',
    'tenant_id, table_code, table_version');

CALL whale_add_index('form_column_meta', 'idx_tenant_id', 'tenant_id');
CALL whale_add_index('form_field_config', 'idx_tenant_id', 'tenant_id');
CALL whale_add_index('form_field_option', 'idx_tenant_id', 'tenant_id');
CALL whale_add_index('form_definition_version', 'idx_tenant_id', 'tenant_id');

CALL whale_backfill_log_tenant('audit_log');
CALL whale_backfill_log_tenant('api_log');
CALL whale_backfill_log_tenant('outbound_log');
CALL whale_backfill_log_tenant('frontend_log');
CALL whale_backfill_log_tenant('behavior_log');

DROP PROCEDURE IF EXISTS whale_backfill_log_tenant;
DROP PROCEDURE IF EXISTS whale_add_tenant_column;
DROP PROCEDURE IF EXISTS whale_add_index;
DROP PROCEDURE IF EXISTS whale_drop_index;
