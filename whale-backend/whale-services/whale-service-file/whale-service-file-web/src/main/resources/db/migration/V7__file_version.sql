-- =====================================================================
-- whale-file 批次 11：file_info 增加文件版本模型
-- =====================================================================
-- 背景：文件版本需要区分「版本组根文件」与「新版本行」。以 parent_id 指向根文件，
--       version 记录组内序号；根版本 parent_id 为 NULL、version 为 1。
-- 兼容：存量行默认 version = 1 且 parent_id = NULL，全部视为根文件，无需回填。
-- 幂等：先查 information_schema，列/索引已存在时跳过，重复执行不报错。
-- =====================================================================
SET @has_parent_id = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND COLUMN_NAME = 'parent_id');
SET @ddl = IF(@has_parent_id = 0,
    'ALTER TABLE file_info ADD COLUMN parent_id BIGINT DEFAULT NULL COMMENT ''版本组根文件ID；根版本为 NULL'' AFTER uploader_id',
    'SELECT ''file_info.parent_id 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_version = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND COLUMN_NAME = 'version');
SET @ddl = IF(@has_version = 0,
    'ALTER TABLE file_info ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT ''版本号：根版本为 1，新版本依次递增'' AFTER parent_id',
    'SELECT ''file_info.version 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_parent_version_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'idx_parent_version');
SET @ddl = IF(@has_parent_version_index = 0,
    'ALTER TABLE file_info ADD INDEX idx_parent_version (parent_id, version)',
    'SELECT ''file_info.idx_parent_version 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
