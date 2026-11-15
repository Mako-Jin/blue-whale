-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V9：主从数据对账差异
-- =====================================================================
-- 背景   : 主表 form_data 与各版本物理表 form_data_{code}_v{version} 双写，
--          任何一侧失败（事务外的物理表操作、人工改库、历史遗留）都会留下单向数据。
--          需要逐表对账并把差异登记下来，供人工确认后修复。
-- 方案   : 新增 form_reconcile_diff 表，按批次记录「主表有物理表无 / 物理表有主表无」；
--          唯一键 (batch_no, physical_table, data_id) 保证同一批次重复登记幂等。
-- 幂等   : CREATE TABLE IF NOT EXISTS；唯一键与索引按 information_schema 判定后补建。
-- =====================================================================

CREATE TABLE IF NOT EXISTS form_reconcile_diff (
    id             BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID（行级隔离）',
    batch_no       VARCHAR(32)  NOT NULL COMMENT '对账批次 yyyyMMddHHmmss',
    physical_table VARCHAR(64)  NOT NULL COMMENT '物理表名',
    data_id        BIGINT       NOT NULL COMMENT '表单数据ID（form_data.id）',
    diff_type      TINYINT      NOT NULL COMMENT '差异类型：1 主表有物理表无 / 2 物理表有主表无',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 待处理 / 1 已修复 / 2 已忽略',
    detail         VARCHAR(512) DEFAULT NULL COMMENT '差异说明',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_table_data (batch_no, physical_table, data_id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '表单主从数据对账差异';

-- ---------------------------------------------------------------------
-- 存量库若已手工建表但缺唯一键，按需补建（幂等）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_reconcile_diff'
        AND INDEX_NAME = 'uk_batch_table_data') = 0,
    'ALTER TABLE form_reconcile_diff ADD UNIQUE KEY uk_batch_table_data (batch_no, physical_table, data_id)',
    'SELECT 1');
PREPARE form_v9_stmt FROM @ddl;
EXECUTE form_v9_stmt;
DEALLOCATE PREPARE form_v9_stmt;

-- ---------------------------------------------------------------------
-- 存量库若已手工建表但缺租户状态索引，按需补建（幂等）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_reconcile_diff'
        AND INDEX_NAME = 'idx_tenant_status') = 0,
    'ALTER TABLE form_reconcile_diff ADD KEY idx_tenant_status (tenant_id, status)',
    'SELECT 1');
PREPARE form_v9_stmt FROM @ddl;
EXECUTE form_v9_stmt;
DEALLOCATE PREPARE form_v9_stmt;
