-- =====================================================================
-- BlueWhale - whale-workflow Flyway 增量 V10：实例办结时间
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 说明     : 归档模块此前以 update_time 近似办结时间，实例后续被迁移/更新会漂移。
--            新增 finished_at，审批通过时写入；历史已通过实例回填 update_time。
-- 幂等     : information_schema 守卫，重复执行安全。
-- =====================================================================

-- 1) 新增办结时间列
SET @has_finished_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance'
      AND COLUMN_NAME = 'finished_at'
);
SET @ddl := IF(@has_finished_at = 0,
    'ALTER TABLE process_instance ADD COLUMN finished_at DATETIME DEFAULT NULL COMMENT ''办结时间（审批通过时写入）'' AFTER status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 历史已通过实例回填办结时间
UPDATE process_instance
SET finished_at = update_time
WHERE status = 1
  AND finished_at IS NULL;

-- 3) 归档游标索引
SET @has_idx_finished := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance'
      AND INDEX_NAME = 'idx_status_finished'
);
SET @ddl := IF(@has_idx_finished = 0,
    'ALTER TABLE process_instance ADD KEY idx_status_finished (status, finished_at)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
