-- =====================================================================
-- 阶段 6：数据生命周期与归档
-- ---------------------------------------------------------------------
-- 1) pay_transaction / pay_notify_task 按 create_time 归档到 *_history：
--    热表只保留近期数据，归档表供审计与追溯；结构与主表一致，主键沿用雪花 ID。
-- 2) 归档口径由 whale.pay.archive.* 控制：
--    - pay_transaction 超过 transaction-retention-days（默认 180）天归档；
--    - pay_notify_task 仅归档 SUCCESS 且超过 notify-success-retention-days（默认 30）天；
--      PENDING/DEAD 永不归档（DEAD 需要人工介入，PENDING 尚未投递成功）。
-- 3) 补充 idx_tenant_create_time：归档扫描按 create_time 过滤，租户维度查询也受益。
-- 幂等口径：CREATE TABLE IF NOT EXISTS / ADD INDEX 先查 information_schema，可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS pay_transaction_history (
    id                BIGINT        NOT NULL            COMMENT '主键ID（与主表一致，雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID',
    order_id          BIGINT        NOT NULL            COMMENT '支付单ID（逻辑关联 pay_order.id）',
    order_no          VARCHAR(64)   NOT NULL            COMMENT '支付单号（冗余存储）',
    type              VARCHAR(16)   NOT NULL            COMMENT '流水类型：PAY 支付 / REFUND 退款',
    channel           VARCHAR(32)   DEFAULT NULL        COMMENT '支付渠道',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '本次流水金额',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    provider_trade_no VARCHAR(64)   DEFAULT NULL        COMMENT '渠道流水号',
    status            VARCHAR(16)   NOT NULL            COMMENT '流水状态：SUCCESS 成功 / FAILED 失败',
    error_message     VARCHAR(512)  DEFAULT NULL        COMMENT '失败原因（成功为 NULL）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_create_time (tenant_id, create_time),
    KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付退款流水归档表';

CREATE TABLE IF NOT EXISTS pay_notify_task_history (
    id            BIGINT         NOT NULL            COMMENT '主键ID（与主表一致，雪花算法）',
    tenant_id     BIGINT         NOT NULL DEFAULT 1  COMMENT '租户ID',
    biz_type      VARCHAR(32)    NOT NULL            COMMENT '业务方类型（MALL_ORDER 等）',
    biz_order_no  VARCHAR(64)    NOT NULL            COMMENT '业务单号（如商城订单号）',
    event         VARCHAR(16)    NOT NULL            COMMENT '事件：PAID / REFUNDED',
    notify_url    VARCHAR(255)   NOT NULL            COMMENT '回调地址（<服务名>/<路径>）',
    payload       VARCHAR(2000)  NOT NULL            COMMENT '回调 JSON 载荷',
    status        VARCHAR(16)    NOT NULL            COMMENT '状态：SUCCESS 成功（归档表只存成功任务）',
    retry_count   INT            NOT NULL DEFAULT 0  COMMENT '已重试次数',
    next_retry_at DATETIME       NOT NULL            COMMENT '下次重试时间',
    last_error    VARCHAR(500)   DEFAULT NULL        COMMENT '最近一次失败原因',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT        NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_create_time (tenant_id, create_time),
    KEY idx_biz_order (tenant_id, biz_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务通知任务归档表';

SET @has_tx_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_transaction' AND INDEX_NAME = 'idx_tenant_create_time');
SET @ddl = IF(@has_tx_index = 0,
    'ALTER TABLE pay_transaction ADD INDEX idx_tenant_create_time (tenant_id, create_time)',
    'SELECT ''pay_transaction.idx_tenant_create_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_notify_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_notify_task' AND INDEX_NAME = 'idx_tenant_create_time');
SET @ddl = IF(@has_notify_index = 0,
    'ALTER TABLE pay_notify_task ADD INDEX idx_tenant_create_time (tenant_id, create_time)',
    'SELECT ''pay_notify_task.idx_tenant_create_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'pay_%_history';
--   SHOW INDEX FROM pay_transaction WHERE Key_name = 'idx_tenant_create_time';
-- =====================================================================
