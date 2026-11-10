-- =====================================================================
-- Wave 5.95 商城 M1 首切片：支付闭环与退款（whale-pay 侧）
-- ---------------------------------------------------------------------
-- 1) pay_order 追加业务方回调信息：biz_type（业务方类型）+ notify_url（回调地址，
--    格式 <服务名>/<路径>，如 whale-mall/mall/internal/order/paid）。
-- 2) pay_refund 退款单：模型支持部分退款，累计退款额在 pay_order.refund_amount 原子累计，
--    全额退完自动置 REFUNDED。
-- 3) pay_notify_task 业务通知任务：支付/退款成功后投递，指数退避重试 6 次后置 DEAD，
--    进程重启不丢通知。
-- 幂等口径：全部 CREATE TABLE IF NOT EXISTS / ADD COLUMN 前置存在性判断，
--           可在任意已应用 V1 的库上重放。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. pay_order 追加回调列
-- ---------------------------------------------------------------------
SET @has_biz_type = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND COLUMN_NAME = 'biz_type');
SET @ddl = IF(@has_biz_type = 0,
    'ALTER TABLE pay_order ADD COLUMN biz_type VARCHAR(32) DEFAULT NULL COMMENT ''业务方类型（MALL_ORDER 等，决定回调路由）'' AFTER business_key',
    'SELECT ''pay_order.biz_type 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_notify_url = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND COLUMN_NAME = 'notify_url');
SET @ddl = IF(@has_notify_url = 0,
    'ALTER TABLE pay_order ADD COLUMN notify_url VARCHAR(255) DEFAULT NULL COMMENT ''业务回调地址（<服务名>/<路径>）'' AFTER biz_type',
    'SELECT ''pay_order.notify_url 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 退款单表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pay_refund (
    id                BIGINT         NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT         NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    refund_no         VARCHAR(64)    NOT NULL            COMMENT '退款单号（租户内唯一）',
    pay_order_id      BIGINT         NOT NULL            COMMENT '支付单ID',
    pay_order_no      VARCHAR(64)    NOT NULL            COMMENT '支付单号（冗余存储）',
    amount            DECIMAL(18,2)  NOT NULL            COMMENT '本次退款金额',
    reason            VARCHAR(255)   DEFAULT NULL        COMMENT '退款原因',
    status            VARCHAR(16)    NOT NULL DEFAULT 'CREATED'
                                                         COMMENT '状态：CREATED 已创建 / SUCCESS 成功 / FAILED 失败',
    channel_refund_no VARCHAR(64)    DEFAULT NULL        COMMENT '渠道退款流水号',
    refund_time       DATETIME       DEFAULT NULL        COMMENT '退款完成时间',
    remark            VARCHAR(255)   DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT        NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_refund_no (tenant_id, refund_no),
    KEY idx_pay_order (pay_order_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='退款单表';

-- ---------------------------------------------------------------------
-- 3. 业务通知任务表
--    回调地址格式 <服务名>/<路径>，投递时经 Nacos 解析实例，与部署端口解耦。
--    载荷携带 tenantId，投递线程以 X-Tenant-Id 还原租户上下文。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pay_notify_task (
    id            BIGINT         NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT         NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    biz_type      VARCHAR(32)    NOT NULL            COMMENT '业务方类型（MALL_ORDER 等）',
    biz_order_no  VARCHAR(64)    NOT NULL            COMMENT '业务单号（如商城订单号）',
    event         VARCHAR(16)    NOT NULL            COMMENT '事件：PAID 支付成功 / REFUNDED 退款成功',
    notify_url    VARCHAR(255)   NOT NULL            COMMENT '回调地址（<服务名>/<路径>）',
    payload       VARCHAR(2000)  NOT NULL            COMMENT '回调 JSON 载荷',
    status        VARCHAR(16)    NOT NULL DEFAULT 'PENDING'
                                                     COMMENT '状态：PENDING 待投递 / SUCCESS 成功 / DEAD 死信（人工介入）',
    retry_count   INT            NOT NULL DEFAULT 0  COMMENT '已重试次数',
    next_retry_at DATETIME       NOT NULL            COMMENT '下次重试时间',
    last_error    VARCHAR(500)   DEFAULT NULL        COMMENT '最近一次失败原因',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT        NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_status_retry (status, next_retry_at),
    KEY idx_biz_order (tenant_id, biz_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='业务通知任务表';

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM pay_order LIKE 'biz_type';
--   SHOW COLUMNS FROM pay_order LIKE 'notify_url';
--   SHOW TABLES LIKE 'pay_%';
-- =====================================================================
