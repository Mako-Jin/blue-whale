-- =====================================================================
-- whale-file 批次 1：file_info 增加落盘存储插件类型
-- =====================================================================
-- 背景：存储插件可随 whale.file.storage.type 切换，但历史记录未标注落盘介质，
--       切换后读取统一走「活动插件」，导致存量文件 404。
-- 回填：此处不写死介质类型（local/minio 环境不同），由应用启动期
--       StorageTypeBackfillRunner 按当前活动插件回填 storage_type IS NULL 的行。
-- 幂等：先查 information_schema，列已存在时跳过，重复执行不报错。
-- =====================================================================
SET @has_storage_type = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND COLUMN_NAME = 'storage_type');
SET @ddl = IF(@has_storage_type = 0,
    'ALTER TABLE file_info ADD COLUMN storage_type VARCHAR(32) DEFAULT NULL COMMENT ''落盘时的存储插件类型（local/minio），存量由启动期回填'' AFTER bucket',
    'SELECT ''file_info.storage_type 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
