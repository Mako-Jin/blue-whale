-- =====================================================================
-- BlueWhale - whale-workflow Flyway 增量 V13：待办到期时间
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 说明     : 流程节点可选配置 timeoutHours（小时），创建待办时换算并固化到 due_at；
--            超时扫描任务据此识别「待办且已到期」，仅做提醒与统计，不自动推进流程。
-- 兼容     : 存量任务 due_at 为 NULL，表示未配置超时，不参与超时扫描。
-- 幂等     : MySQL 不支持 ADD COLUMN / ADD INDEX IF NOT EXISTS，使用 information_schema 守卫。
-- =====================================================================

SET @has_due_at := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'due_at'
);
SET @ddl := IF(@has_due_at = 0,
    'ALTER TABLE process_task ADD COLUMN due_at DATETIME DEFAULT NULL COMMENT ''到期时间（节点配置超时时长时由创建时间推算；为空表示不设超时）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_due_at_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND INDEX_NAME = 'idx_status_due_at'
);
SET @ddl := IF(@has_due_at_index = 0,
    'ALTER TABLE process_task ADD INDEX idx_status_due_at (status, due_at)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
