-- =====================================================================
-- Wave 5.80 M1B：PDM BOM 多视图派生（EBOM → PBOM/MBOM）
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 扩展列逐列检查 information_schema，缺失才 ADD COLUMN；
--            索引先查 STATISTICS 缺失才 CREATE INDEX。
--            本脚本不含 DROP / TRUNCATE / DELETE。
-- 兼容     : 存量 pdm_bom 数据保持 NULL，视为原生视图（无派生来源）。
-- 边界     : 派生视图独立存储与发布，不回写源 EBOM。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 记录派生来源（源 BOM + 源版本）
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_bom' AND COLUMN_NAME = 'source_bom_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_bom ADD COLUMN source_bom_id BIGINT DEFAULT NULL COMMENT ''派生来源 BOM ID'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_bom' AND COLUMN_NAME = 'source_version_no');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_bom ADD COLUMN source_version_no VARCHAR(20) DEFAULT NULL COMMENT ''派生来源版本号'' AFTER source_bom_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 派生来源索引（按来源 + 视图查找已派生版本）
-- ---------------------------------------------------------------------
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_bom' AND INDEX_NAME = 'idx_bom_source');
SET @ddl := IF(@idx_exists = 0,
    'CREATE INDEX idx_bom_source ON pdm_bom (source_bom_id, view)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
