-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V17
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 里程碑   : roadmap 11.3 报销单关联出差单 + 预算超限审批变量
-- 说明     : oa_expense 增加关联出差单ID与出差预算快照。
--            预算快照在报销单创建/更新时写入，后续出差预算变更不回写。
-- 幂等     : 逐列 / 索引 information_schema 守卫，缺失才加。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 关联出差单ID
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_expense' AND COLUMN_NAME = 'trip_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE oa_expense ADD COLUMN trip_id BIGINT NULL COMMENT ''关联出差单ID'' AFTER erp_payable_no',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 关联出差预算快照（元，未关联为 0.00）
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_expense' AND COLUMN_NAME = 'trip_budget_amount');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE oa_expense ADD COLUMN trip_budget_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT ''关联出差预算快照（元）'' AFTER trip_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 关联出差单查询索引
-- ---------------------------------------------------------------------
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_expense' AND INDEX_NAME = 'idx_oa_expense_trip');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE oa_expense ADD INDEX idx_oa_expense_trip (tenant_id, trip_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
