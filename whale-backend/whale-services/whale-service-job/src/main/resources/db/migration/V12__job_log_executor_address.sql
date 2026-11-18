-- job_log 增加「实际派发到的执行器地址」：
-- 执行日志落在执行器本地，多副本下只有真正跑过这一次的那台实例才有文件，
-- 调度中心回捞必须按地址精确请求，否则会拿到别的实例日志或直接 404。
-- 用 information_schema 先判存再 ALTER：新库由 init.sql 一次建好该列，直接 ADD 会报重复列。
SET @add_executor_address = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE job_log ADD COLUMN executor_address VARCHAR(128) DEFAULT NULL COMMENT ''实际派发到的执行器地址（host:port）'' AFTER shard_index',
        'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'job_log'
      AND COLUMN_NAME = 'executor_address'
);
PREPARE add_executor_address_stmt FROM @add_executor_address;
EXECUTE add_executor_address_stmt;
DEALLOCATE PREPARE add_executor_address_stmt;
