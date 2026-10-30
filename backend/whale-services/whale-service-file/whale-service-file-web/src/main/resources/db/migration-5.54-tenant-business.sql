-- ============================================================================
-- Wave 5.54 Phase 3：文件表租户化（幂等脚本）
-- 适用库   : bluewhale_file
-- 执行方式 : mysql -u<user> -p bluewhale_file < migration-5.54-tenant-business.sql
-- 说明：新增 tenant_id（默认 1），存量数据自动归属默认租户；
--       对象键租户前缀与下载校验已在 Phase 2 完成；历史日志回填默认租户。
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

CALL whale_add_tenant_column('file_info');
CALL whale_drop_index('file_info', 'idx_md5');
CALL whale_add_index('file_info', 'idx_tenant_id', 'tenant_id');
CALL whale_add_index('file_info', 'idx_tenant_md5', 'tenant_id, md5');

CALL whale_backfill_log_tenant('audit_log');
CALL whale_backfill_log_tenant('api_log');
CALL whale_backfill_log_tenant('outbound_log');
CALL whale_backfill_log_tenant('frontend_log');
CALL whale_backfill_log_tenant('behavior_log');

DROP PROCEDURE IF EXISTS whale_backfill_log_tenant;
DROP PROCEDURE IF EXISTS whale_add_tenant_column;
DROP PROCEDURE IF EXISTS whale_add_index;
DROP PROCEDURE IF EXISTS whale_drop_index;
