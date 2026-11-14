-- =====================================================================
-- BlueWhale whale-workflow 增量迁移脚本（Wave 5.32：节点表单绑定与画布坐标）
-- =====================================================================
-- 适用场景 : 已按 Wave 5.31 部署、库中已存在 process_task 表的运行库
--             （保留存量待办数据）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步这 3 列）。
-- 说明     : 本脚本幂等（通过 information_schema 存在性守卫，规避 MySQL 8.0
--             不支持 ADD COLUMN IF NOT EXISTS 而重复执行报错）。新增列
--             node_key / form_id / form_code 记录建任务时的节点键与节点绑定的
--             表单定义 ID / 编码；存量行为 NULL，不影响既有流转语义。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 待办任务表：新增节点键列（存量行 NULL，回落 node_index 语义）
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_task ADD COLUMN node_key VARCHAR(64) DEFAULT NULL COMMENT ''建任务时的节点键（规范化模型用 key；legacy 用合成 key n{i+1}）'' AFTER node_name',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'node_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 待办任务表：新增表单定义 ID 列（存量行 NULL）
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_task ADD COLUMN form_id VARCHAR(64) DEFAULT NULL COMMENT ''节点绑定的表单定义 ID（字符串承载，可空）'' AFTER node_key',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'form_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 待办任务表：新增表单编码列（存量行 NULL）
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE process_task ADD COLUMN form_code VARCHAR(64) DEFAULT NULL COMMENT ''节点绑定的表单编码（可空）'' AFTER form_id',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_task' AND COLUMN_NAME = 'form_code');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
