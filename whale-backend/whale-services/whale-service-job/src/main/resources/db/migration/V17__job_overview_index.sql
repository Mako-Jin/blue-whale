-- =====================================================================
-- 三维王城时辰殿（T5.5）：定时调度概览所需的三个索引
-- ---------------------------------------------------------------------
-- 概览每次要问 6 个数：任务总数与运行中 / 今日执行与成败 / 失败任务面 /
-- 静默任务 / 近 7 日趋势 / 失败 Top。它们的形状只有两种：
--   ① 「本租户 + 时间窗」（job_log）
--   ② 「本租户 + 状态 + 某任务有没有近期执行」（job_info 驱动 NOT EXISTS 子查询）
-- 而 V1 的 job_log 只有 idx_tenant_id / idx_job_id / idx_start_time 三个单列索引，
-- MySQL 只能选一个用，另一个条件退化成回表过滤。总控是 30s 一次的常驻轮询，
-- 这种退化会被放大成持续负载。
--
-- 三个索引各自对应一类查询：
--   1) job_log (tenant_id, start_time)               执行统计 / 按天趋势
--   2) job_log (tenant_id, status, start_time)       失败次数、失败任务去重、失败 Top
--   3) job_info (tenant_id, status)                  任务总数与运行中、静默任务扫描
--
-- idx_job_id 被 4) job_log (job_id, start_time) 最左前缀完全覆盖，且它正是静默判定
-- 那条 NOT EXISTS 子查询的形状（先按 job_id 定位、再按 start_time 卡窗口），一并用它替换。
--
-- 幂等口径：先查 information_schema.STATISTICS 再决定是否 ADD INDEX，
--           可在任意已应用 V16 的库上重放。
-- =====================================================================

SET @has_tenant_start = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND INDEX_NAME = 'idx_tenant_start');
SET @ddl = IF(@has_tenant_start = 0,
    'ALTER TABLE job_log ADD INDEX idx_tenant_start (tenant_id, start_time)',
    'SELECT ''job_log.idx_tenant_start 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_status_start = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND INDEX_NAME = 'idx_tenant_status_start');
SET @ddl = IF(@has_tenant_status_start = 0,
    'ALTER TABLE job_log ADD INDEX idx_tenant_status_start (tenant_id, status, start_time)',
    'SELECT ''job_log.idx_tenant_status_start 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_job_start = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND INDEX_NAME = 'idx_job_start');
SET @ddl = IF(@has_job_start = 0,
    'ALTER TABLE job_log ADD INDEX idx_job_start (job_id, start_time)',
    'SELECT ''job_log.idx_job_start 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_status = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_info' AND INDEX_NAME = 'idx_tenant_status');
SET @ddl = IF(@has_tenant_status = 0,
    'ALTER TABLE job_info ADD INDEX idx_tenant_status (tenant_id, status)',
    'SELECT ''job_info.idx_tenant_status 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 冗余单列索引：只有在新复合索引确实存在时才删，避免在只跑了一半的库上
-- 把 job_log 唯一的 job_id 索引删掉
SET @has_job_start_ready = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND INDEX_NAME = 'idx_job_start');
SET @has_job_id_only = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND INDEX_NAME = 'idx_job_id');
SET @ddl = IF(@has_job_start_ready > 0 AND @has_job_id_only > 0,
    'ALTER TABLE job_log DROP INDEX idx_job_id',
    'SELECT ''job_log.idx_job_id 无需删除（不存在或复合索引未就绪），跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
