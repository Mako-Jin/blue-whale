-- =====================================================================
-- Wave 5.80 M1B-4：PDM 零件人工合并（源件合并目标）
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 逐列/逐索引检查 information_schema，缺失才执行 DDL；
--            本脚本不含 DROP / TRUNCATE / DELETE。
-- 兼容     : 存量零件 merged_to 保持 NULL，表示未参与合并。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 合并目标（源件停用后仍可追溯保留件）
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND COLUMN_NAME = 'merged_to');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_product ADD COLUMN merged_to BIGINT DEFAULT NULL COMMENT ''合并目标零件ID'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 合并目标索引（按目标反查被合并零件）
-- ---------------------------------------------------------------------
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND INDEX_NAME = 'idx_product_merged_to');
SET @ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_product_merged_to ON pdm_product (merged_to)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
