-- =====================================================================
-- BlueWhale whale-job 增量迁移脚本（P2：失败重试策略 / 任务分片调度）
-- =====================================================================
-- 适用场景 : 已按既有版本部署、库中已存在 job_info / job_log 表的运行库
--             （保留存量任务与执行日志）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 5 列）。
-- 说明     : 本脚本幂等（通过 information_schema.COLUMNS 存在性守卫，规避
--             MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS 而重复执行报错）。
--             · job_info.max_retry                失败重试次数上限（0 不重试）
--             · job_info.retry_interval_seconds   重试间隔秒数（默认 60）
--             · job_info.shard_total              分片总数（1 不分片，上限 16）
--             · job_log.retry_count               本次执行已重试次数
--             · job_log.shard_index               当前分片序号（从 0 开始）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 定时任务信息表：失败重试次数上限
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE job_info ADD COLUMN max_retry INT NOT NULL DEFAULT 0 COMMENT ''失败重试次数上限：0 表示不重试（建议不超过 5）'' AFTER misfire_policy',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_info' AND COLUMN_NAME = 'max_retry');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 定时任务信息表：重试间隔秒数
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE job_info ADD COLUMN retry_interval_seconds INT NOT NULL DEFAULT 60 COMMENT ''重试间隔秒数'' AFTER max_retry',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_info' AND COLUMN_NAME = 'retry_interval_seconds');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 定时任务信息表：分片总数
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE job_info ADD COLUMN shard_total INT NOT NULL DEFAULT 1 COMMENT ''分片总数：1 表示不分片（建议不超过 16）'' AFTER retry_interval_seconds',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_info' AND COLUMN_NAME = 'shard_total');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 定时任务执行日志表：本次执行已重试次数
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE job_log ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT ''本次执行已重试次数（首次执行为 0）'' AFTER status',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND COLUMN_NAME = 'retry_count');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 5. 定时任务执行日志表：当前分片序号
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE job_log ADD COLUMN shard_index INT NOT NULL DEFAULT 0 COMMENT ''当前分片序号（从 0 开始；不分片为 0）'' AFTER retry_count',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND COLUMN_NAME = 'shard_index');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
