-- =====================================================================
-- whale-file 批次 2：file_info 记录真实逻辑删除时间
-- =====================================================================
-- 背景：回收站此前用 update_time 冒充删除时间，无法区分「业务更新」与「删除时刻」，
--       也无法支撑到期物理清理。
-- 回填：存量已删行以 update_time 作为删除时间（删除后未再发生业务更新）。
-- 幂等：先查 information_schema，列已存在时跳过；回填条件带 IS NULL，可重复执行。
-- =====================================================================
SET @has_delete_time = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND COLUMN_NAME = 'delete_time');
SET @ddl = IF(@has_delete_time = 0,
    'ALTER TABLE file_info ADD COLUMN delete_time DATETIME DEFAULT NULL COMMENT ''逻辑删除时间；恢复时置空'' AFTER deleted',
    'SELECT ''file_info.delete_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE file_info SET delete_time = update_time WHERE deleted = 1 AND delete_time IS NULL;
