-- =====================================================================
-- BlueWhale - whale-workflow Flyway 增量 V12：内网完成事件幂等列
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 说明     : 内网完成回调携带 eventId，重复回调需返回幂等结果而非再次推进流程；
--            在 process_task 记录最近一次完成事件ID，配合「仍为待办」条件更新实现幂等。
-- 幂等     : MySQL 不支持 ADD COLUMN IF NOT EXISTS，使用 information_schema 守卫。
-- =====================================================================

SET @has_last_event_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'last_event_id'
);
SET @ddl := IF(@has_last_event_id = 0,
    'ALTER TABLE process_task ADD COLUMN last_event_id VARCHAR(64) DEFAULT NULL COMMENT ''最近一次内网完成事件ID（同一事件重复回调时用于幂等）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
