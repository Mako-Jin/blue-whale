-- =====================================================================
-- 韧性增强：任务级超时、连续失败自动暂停（第 2、3 项）
-- =====================================================================
-- 1) timeout_seconds：单次执行超时秒数，0 表示沿用全局默认（全局默认 0 = 不限制）；
-- 2) fail_threshold：连续失败自动暂停阈值，0 表示关闭熔断；
-- 3) fail_count    ：当前连续失败次数，成功执行后清零，只由调度引擎维护。
-- 用 information_schema 先判存再 ALTER：新库由后续基线一次建好该列，直接 ADD 会报重复列。
-- =====================================================================

SET @add_timeout_seconds = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_info ADD COLUMN timeout_seconds INT NOT NULL DEFAULT 0 COMMENT ''单次执行超时秒数：0 表示沿用全局默认'' AFTER shard_total',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'job_info'
      AND COLUMN_NAME = 'timeout_seconds'
);
PREPARE add_timeout_seconds_stmt FROM @add_timeout_seconds;
EXECUTE add_timeout_seconds_stmt;
DEALLOCATE PREPARE add_timeout_seconds_stmt;

SET @add_fail_threshold = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_info ADD COLUMN fail_threshold INT NOT NULL DEFAULT 0 COMMENT ''连续失败自动暂停阈值：0 表示关闭'' AFTER timeout_seconds',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'job_info'
      AND COLUMN_NAME = 'fail_threshold'
);
PREPARE add_fail_threshold_stmt FROM @add_fail_threshold;
EXECUTE add_fail_threshold_stmt;
DEALLOCATE PREPARE add_fail_threshold_stmt;

SET @add_fail_count = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_info ADD COLUMN fail_count INT NOT NULL DEFAULT 0 COMMENT ''当前连续失败次数（成功后清零）'' AFTER fail_threshold',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'job_info'
      AND COLUMN_NAME = 'fail_count'
);
PREPARE add_fail_count_stmt FROM @add_fail_count;
EXECUTE add_fail_count_stmt;
DEALLOCATE PREPARE add_fail_count_stmt;
