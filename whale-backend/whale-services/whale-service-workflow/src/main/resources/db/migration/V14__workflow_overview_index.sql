-- =====================================================================
-- 三维王城政事殿（T5.6）：工作流概览所需的四个索引
-- ---------------------------------------------------------------------
-- 概览每轮要问 6 个数：运行中 / 今日发起 / 今日办结 / 待办总数 / 超时待办 /
-- 卡住实例（运行中且无待办），外加近 7 日趋势与待办积压 Top5。它们的形状只有三种：
--   ① 「本租户 + 状态（+ 时间窗）」          process_instance / process_task
--   ② 「本租户 + 时间窗，按 DATE() 分组」     process_instance
--   ③ 「本租户 + 状态，按 node_name 分组」    process_task
-- V1 的索引是 tenant_id / definition_id / status / assignee 这类单列索引，
-- MySQL 每次只能选一个，其余条件退化成回表过滤；总控 30s 一次的常驻轮询会把
-- 这种退化放大成持续负载。
--
-- 四个索引各自对应一类查询：
--   1) process_instance (tenant_id, status, finished_at)   运行中数 / 今日办结 / 按天办结 / 卡住实例
--   2) process_instance (tenant_id, create_time)           今日发起 / 按天发起
--   3) process_task     (tenant_id, status, due_at)        待办总数 / 超时待办（含最久超时）
--   4) process_task     (tenant_id, status, node_name)     待办积压 Top5（分组列在末尾，省一次排序）
--
-- 不动的三个：idx_status_finished(status, finished_at) 服务 2D 归档侧跨租户扫描，
-- idx_status_due_at(status, due_at) 服务 TimeoutScheduler 的后台跨租户扫描
-- （后台线程无租户上下文，前面带 tenant_id 的索引在那里用不上），idx_instance_id 供
-- 卡住判定那条 NOT EXISTS 子查询按 instance_id 定位。
--
-- 冗余单列索引：idx_tenant_id 被上面两个复合索引的最左前缀完全覆盖，一并删除，
-- 减少创建实例 / 创建任务链路的索引维护开销。
--
-- 幂等口径：先查 information_schema.STATISTICS 再决定是否 ADD / DROP INDEX，
--           可在任意已应用 V13 的库上重放。
-- =====================================================================

SET @has_tenant_status_finished = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance'
      AND INDEX_NAME = 'idx_tenant_status_finished');
SET @ddl = IF(@has_tenant_status_finished = 0,
    'ALTER TABLE process_instance ADD INDEX idx_tenant_status_finished (tenant_id, status, finished_at)',
    'SELECT ''process_instance.idx_tenant_status_finished 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_create = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance'
      AND INDEX_NAME = 'idx_tenant_create_time');
SET @ddl = IF(@has_tenant_create = 0,
    'ALTER TABLE process_instance ADD INDEX idx_tenant_create_time (tenant_id, create_time)',
    'SELECT ''process_instance.idx_tenant_create_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_status_due = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task'
      AND INDEX_NAME = 'idx_tenant_status_due');
SET @ddl = IF(@has_tenant_status_due = 0,
    'ALTER TABLE process_task ADD INDEX idx_tenant_status_due (tenant_id, status, due_at)',
    'SELECT ''process_task.idx_tenant_status_due 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_status_node = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task'
      AND INDEX_NAME = 'idx_tenant_status_node');
SET @ddl = IF(@has_tenant_status_node = 0,
    'ALTER TABLE process_task ADD INDEX idx_tenant_status_node (tenant_id, status, node_name)',
    'SELECT ''process_task.idx_tenant_status_node 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 冗余单列索引：只有在新复合索引确实存在时才删，避免在只跑了一半的库上
-- 把两张表唯一的 tenant_id 索引删掉。
-- 这里必须重新查一次：上面的变量是"加索引之前"的快照。
SET @instance_ready = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance'
      AND INDEX_NAME = 'idx_tenant_status_finished');
SET @instance_tenant_only = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance'
      AND INDEX_NAME = 'idx_tenant_id');
SET @ddl = IF(@instance_ready > 0 AND @instance_tenant_only > 0,
    'ALTER TABLE process_instance DROP INDEX idx_tenant_id',
    'SELECT ''process_instance.idx_tenant_id 无需删除（不存在或复合索引未就绪），跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @task_ready = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task'
      AND INDEX_NAME = 'idx_tenant_status_due');
SET @task_tenant_only = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task'
      AND INDEX_NAME = 'idx_tenant_id');
SET @ddl = IF(@task_ready > 0 AND @task_tenant_only > 0,
    'ALTER TABLE process_task DROP INDEX idx_tenant_id',
    'SELECT ''process_task.idx_tenant_id 无需删除（不存在或复合索引未就绪），跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
