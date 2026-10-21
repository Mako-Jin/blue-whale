-- =====================================================================
-- BlueWhale whale-workflow 增量迁移脚本（Wave 5.35：条件变量 / 并行 / 会签 / 转办委派）
-- =====================================================================
-- 适用场景 : 已按 Wave 5.32 部署、库中已存在 process_instance / process_task 表的运行库
--             （保留存量流程数据）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 7 列）。
-- 说明     : 本脚本幂等（通过 information_schema 存在性守卫，规避 MySQL 8.0
--             不支持 ADD COLUMN IF NOT EXISTS 而重复执行报错）。新增列全部
--             DEFAULT NULL，存量行不受影响：
--             · process_instance.variables_json    条件选路所需流程变量快照
--             · process_instance.active_node_keys  并行分支同时活动的节点键
--             · process_task.parent_node_key       并行分支父节点键
--             · process_task.sign_type/sign_policy 会签任务类型与策略快照
--             · process_task.owner_id              转办归属人
--             · process_task.delegate_from         委派来源人
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 流程实例表：新增流程变量快照列
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_instance ADD COLUMN variables_json TEXT DEFAULT NULL COMMENT ''流程变量快照（JSON，供条件选路重放）'' AFTER current_node_key',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance' AND COLUMN_NAME = 'variables_json');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 流程实例表：新增并行活动节点键列
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_instance ADD COLUMN active_node_keys VARCHAR(512) DEFAULT NULL COMMENT ''并行分支活动节点键（逗号分隔；为空表示单待办）'' AFTER current_node_key',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance' AND COLUMN_NAME = 'active_node_keys');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 待办任务表：新增并行分支父节点键列
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_task ADD COLUMN parent_node_key VARCHAR(64) DEFAULT NULL COMMENT ''并行分支父节点键（fork 网关键；非并行任务为空）'' AFTER comment',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'parent_node_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 待办任务表：新增会签任务类型列
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_task ADD COLUMN sign_type VARCHAR(32) DEFAULT NULL COMMENT ''任务类型：COUNTER_SIGN 会签 / 空 普通审批'' AFTER parent_node_key',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'sign_type');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 5. 待办任务表：新增会签策略快照列
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_task ADD COLUMN sign_policy VARCHAR(16) DEFAULT NULL COMMENT ''会签策略快照：ALL / ANY / RATIO:n'' AFTER sign_type',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'sign_policy');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 6. 待办任务表：新增转办归属人列
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_task ADD COLUMN owner_id VARCHAR(64) DEFAULT NULL COMMENT ''任务归属人（转办后仍记录原审批人）'' AFTER sign_policy',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'owner_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 7. 待办任务表：新增委派来源人列
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_task ADD COLUMN delegate_from VARCHAR(64) DEFAULT NULL COMMENT ''委派来源人（委派任务记录原审批人）'' AFTER owner_id',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'delegate_from');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
