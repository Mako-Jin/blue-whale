-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V8：表单数据变更历史
-- =====================================================================
-- 背景   : form_data 只保存当前值，update 直接覆盖 dataJson，合规/审计场景无法回答
--          「谁在何时把字段 A 从 X 改成 Y」。表定义有版本快照，数据本身没有。
-- 方案   : 新增只追加的 form_data_history 表，submit/update/delete 时各写一条；
--          update 额外记录变更字段集合与前后快照。
-- 幂等   : CREATE TABLE IF NOT EXISTS；索引按 information_schema.STATISTICS 判定后补建。
-- =====================================================================

CREATE TABLE IF NOT EXISTS form_data_history (
    id              BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID（行级隔离）',
    data_id         BIGINT       NOT NULL COMMENT '表单数据ID（form_data.id）',
    definition_code VARCHAR(64)  NOT NULL COMMENT '表单编码（冗余，便于按表单查询）',
    change_type     VARCHAR(16)  NOT NULL COMMENT '变更类型：CREATE / UPDATE / DELETE',
    before_json     MEDIUMTEXT   NULL COMMENT '变更前数据JSON（CREATE 为空）',
    after_json      MEDIUMTEXT   NULL COMMENT '变更后数据JSON（DELETE 为空）',
    changed_keys    VARCHAR(1024) DEFAULT NULL COMMENT '本次变更的字段编码（逗号分隔）',
    operator        VARCHAR(64)  DEFAULT NULL COMMENT '操作人账号',
    change_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (id),
    KEY idx_tenant_data (tenant_id, data_id, change_time),
    KEY idx_definition_code (definition_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '表单数据变更历史（只追加）';

-- ---------------------------------------------------------------------
-- 存量库若已手工建表但缺索引，按需补建（幂等）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_data_history'
        AND INDEX_NAME = 'idx_tenant_data') = 0,
    'ALTER TABLE form_data_history ADD KEY idx_tenant_data (tenant_id, data_id, change_time)',
    'SELECT 1');
PREPARE form_v8_stmt FROM @ddl;
EXECUTE form_v8_stmt;
DEALLOCATE PREPARE form_v8_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_data_history'
        AND INDEX_NAME = 'idx_definition_code') = 0,
    'ALTER TABLE form_data_history ADD KEY idx_definition_code (definition_code)',
    'SELECT 1');
PREPARE form_v8_stmt FROM @ddl;
EXECUTE form_v8_stmt;
DEALLOCATE PREPARE form_v8_stmt;
