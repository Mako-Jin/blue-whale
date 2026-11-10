-- ============================================================================
-- Wave 5.54 Phase 3：档案表租户化（幂等脚本）
-- 适用库   : bluewhale_archive
-- 执行方式 : mysql -u<user> -p bluewhale_archive < migration-5.54-tenant-business.sql
-- 说明：7 张档案表新增 tenant_id（默认 1）；档号/门类/规则/来源/流水/游标唯一键
--       统一升级为租户复合键。
-- ============================================================================

DROP PROCEDURE IF EXISTS whale_add_tenant_column;
DROP PROCEDURE IF EXISTS whale_add_index;
DROP PROCEDURE IF EXISTS whale_add_unique;
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

CREATE PROCEDURE whale_add_unique(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(128))
BEGIN
    IF NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' ADD UNIQUE KEY ', idx, ' (', cols, ')');
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

CALL whale_add_tenant_column('archive_category');
CALL whale_add_tenant_column('archive_rule');
CALL whale_add_tenant_column('archive_item');
CALL whale_add_tenant_column('archive_file');
CALL whale_add_tenant_column('archive_source_record');
CALL whale_add_tenant_column('archive_sequence');
CALL whale_add_tenant_column('archive_sync_state');

CALL whale_drop_index('archive_category', 'uk_code');
CALL whale_add_unique('archive_category', 'uk_tenant_code', 'tenant_id, code');

CALL whale_drop_index('archive_rule', 'uk_definition_code');
CALL whale_add_unique('archive_rule', 'uk_tenant_definition_code', 'tenant_id, definition_code');

CALL whale_drop_index('archive_item', 'uk_archive_no');
CALL whale_drop_index('archive_item', 'uk_source');
CALL whale_add_unique('archive_item', 'uk_tenant_archive_no', 'tenant_id, archive_no');
CALL whale_add_unique('archive_item', 'uk_tenant_source', 'tenant_id, source_type, source_id');

CALL whale_add_index('archive_file', 'idx_tenant_id', 'tenant_id');

CALL whale_drop_index('archive_source_record', 'uk_source');
CALL whale_add_unique('archive_source_record', 'uk_tenant_source', 'tenant_id, source_type, source_id');

CALL whale_drop_index('archive_sequence', 'uk_category_year');
CALL whale_add_unique('archive_sequence', 'uk_tenant_category_year', 'tenant_id, category_code, year');

CALL whale_drop_index('archive_sync_state', 'uk_name');
CALL whale_add_unique('archive_sync_state', 'uk_tenant_name', 'tenant_id, name');

CALL whale_backfill_log_tenant('audit_log');
CALL whale_backfill_log_tenant('api_log');
CALL whale_backfill_log_tenant('outbound_log');
CALL whale_backfill_log_tenant('frontend_log');
CALL whale_backfill_log_tenant('behavior_log');

DROP PROCEDURE IF EXISTS whale_backfill_log_tenant;
DROP PROCEDURE IF EXISTS whale_add_tenant_column;
DROP PROCEDURE IF EXISTS whale_add_index;
DROP PROCEDURE IF EXISTS whale_add_unique;
DROP PROCEDURE IF EXISTS whale_drop_index;
