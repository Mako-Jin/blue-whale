-- =====================================================================
-- 渠道异步回调日志表（阶段 2：回调入站）
-- ---------------------------------------------------------------------
-- 1) 验签通过先落日志，处理结果回写，用于攻击面观测与问题追溯。
-- 2) idempotency_key = channel + ':' + 渠道流水号 + ':' + event，唯一键兜底重发；
--    验签失败等无法拿到流水号的请求使用随机后缀，保证每次尝试都可见。
-- 3) 本表不参与租户插件自动过滤（tenant_id 允许为空，由支付单反查后回填）：
--    回调请求本身不信任 X-Tenant-Id，Mapper 通过 @InterceptorIgnore 显式忽略。
-- 幂等口径：CREATE TABLE IF NOT EXISTS，可在任意已应用 V1~V4 的库上重放。
-- =====================================================================

CREATE TABLE IF NOT EXISTS pay_channel_callback_log (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花）',
    tenant_id         BIGINT        DEFAULT NULL        COMMENT '租户ID（由支付单反查，未匹配为 NULL）',
    channel           VARCHAR(32)   NOT NULL            COMMENT '渠道编码',
    channel_trade_no  VARCHAR(64)   DEFAULT NULL        COMMENT '渠道流水号',
    out_trade_no      VARCHAR(64)   DEFAULT NULL        COMMENT '商户支付单号',
    event             VARCHAR(32)   NOT NULL            COMMENT '事件：PAID/REFUNDED/CLOSED/UNKNOWN',
    idempotency_key   VARCHAR(128)  NOT NULL            COMMENT '幂等键：channel+tradeNo+event',
    result            VARCHAR(24)   NOT NULL            COMMENT '结果：PROCESSED/DUPLICATE/REJECTED/FAILED',
    reject_reason     VARCHAR(64)   DEFAULT NULL        COMMENT '拒绝原因：SIGN_INVALID/AMOUNT_MISMATCH/ORDER_NOT_FOUND/REFUND_NOT_FOUND/STATUS_CONFLICT/EVENT_UNSUPPORTED',
    amount            DECIMAL(18,2) DEFAULT NULL        COMMENT '渠道金额',
    currency          VARCHAR(8)    DEFAULT NULL        COMMENT '渠道币种',
    raw_body          TEXT          NULL                COMMENT '原始报文（截断后入库）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency (idempotency_key),
    KEY idx_tenant_trade (tenant_id, channel_trade_no),
    KEY idx_out_trade (out_trade_no),
    KEY idx_result_time (result, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='渠道回调日志表';

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'pay_channel_callback_log';
--   SHOW INDEX FROM pay_channel_callback_log;
-- =====================================================================
