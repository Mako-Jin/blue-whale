-- =====================================================================
-- Wave 5.61 M3：流程任务操作来源字段增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 执行方式 : mysql -u<user> -p bluewhale_workflow < migration-5.63-workflow-task-operator.sql
-- 说明     : 内网完成接口由系统/回调驱动，需记录操作人、操作类型与来源；
--            MySQL 不支持 ADD COLUMN IF NOT EXISTS，使用 information_schema 守卫。
-- =====================================================================

SET @has_operator_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'operator_id'
);
SET @ddl := IF(@has_operator_id = 0,
    'ALTER TABLE process_task ADD COLUMN operator_id BIGINT DEFAULT NULL COMMENT ''实际完成人用户ID（系统完成时为系统账号）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_operator_type := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'operator_type'
);
SET @ddl := IF(@has_operator_type = 0,
    'ALTER TABLE process_task ADD COLUMN operator_type VARCHAR(16) DEFAULT NULL COMMENT ''操作类型：USER 用户 / SYSTEM 系统/回调''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_operator_source := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'operator_source'
);
SET @ddl := IF(@has_operator_source = 0,
    'ALTER TABLE process_task ADD COLUMN operator_source VARCHAR(64) DEFAULT NULL COMMENT ''操作来源：ESIGN_CALLBACK / MANUAL / SCHEDULER 等''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
