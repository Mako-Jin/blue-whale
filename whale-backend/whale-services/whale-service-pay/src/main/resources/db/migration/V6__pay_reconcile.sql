-- =====================================================================
-- 批量对账与差异闭环（阶段 3）
-- ---------------------------------------------------------------------
-- 1) pay_reconcile_batch：按「租户 + 渠道 + 业务日」生成批次，唯一键同时承担幂等与
--    单实例执行抢占：重复触发只有插入成功者执行，其余读取已有批次直接返回。
-- 2) pay_reconcile_diff：差异明细，uk_diff 兜底同一批次重复写入；
--    handle_status 驱动 OPEN → CONFIRMED / COMPENSATED / SUSPENDED。
-- 3) 取不到渠道账单时批次落 FAILED 且不产出差异，避免把「取不到账单」误判为
--    「渠道无单」而产出大量 LOCAL_ONLY 假差异。
-- 幂等口径：CREATE TABLE IF NOT EXISTS，可在任意已应用 V1~V5 的库上重放。
-- =====================================================================

CREATE TABLE IF NOT EXISTS pay_reconcile_batch (
    id             BIGINT       NOT NULL            COMMENT '主键ID（雪花）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID',
    channel        VARCHAR(32)  NOT NULL            COMMENT '渠道编码',
    biz_date       DATE         NOT NULL            COMMENT '对账业务日',
    status         VARCHAR(16)  NOT NULL DEFAULT 'RUNNING'
                                                    COMMENT '状态：RUNNING/SUCCESS/FAILED/PARTIAL',
    local_count    INT          NOT NULL DEFAULT 0  COMMENT '本地记录数',
    channel_count  INT          NOT NULL DEFAULT 0  COMMENT '渠道记录数',
    diff_count     INT          NOT NULL DEFAULT 0  COMMENT '差异数',
    started_at     DATETIME     NOT NULL            COMMENT '开始时间',
    finished_at    DATETIME     DEFAULT NULL        COMMENT '结束时间',
    error_message  VARCHAR(512) DEFAULT NULL        COMMENT '失败原因（截断）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch (tenant_id, channel, biz_date),
    KEY idx_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对账批次表';

CREATE TABLE IF NOT EXISTS pay_reconcile_diff (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID',
    batch_id          BIGINT        NOT NULL            COMMENT '批次ID',
    channel           VARCHAR(32)   NOT NULL            COMMENT '渠道编码',
    biz_date          DATE          NOT NULL            COMMENT '对账业务日',
    order_no          VARCHAR(64)   DEFAULT NULL        COMMENT '支付单号',
    channel_trade_no  VARCHAR(64)   DEFAULT NULL        COMMENT '渠道流水号',
    diff_type         VARCHAR(24)   NOT NULL            COMMENT 'LOCAL_ONLY/CHANNEL_ONLY/STATUS_MISMATCH/AMOUNT_MISMATCH',
    local_status      VARCHAR(16)   DEFAULT NULL        COMMENT '本地状态',
    channel_status    VARCHAR(16)   DEFAULT NULL        COMMENT '渠道状态',
    local_amount      DECIMAL(18,2) DEFAULT NULL        COMMENT '本地金额',
    channel_amount    DECIMAL(18,2) DEFAULT NULL        COMMENT '渠道金额',
    currency          VARCHAR(8)    DEFAULT NULL        COMMENT '币种',
    handle_status     VARCHAR(16)   NOT NULL DEFAULT 'OPEN'
                                                        COMMENT '处置状态：OPEN/CONFIRMED/COMPENSATED/SUSPENDED',
    handle_remark     VARCHAR(255)  DEFAULT NULL        COMMENT '处置备注',
    handled_by        BIGINT        DEFAULT NULL        COMMENT '处置人',
    handled_at        DATETIME      DEFAULT NULL        COMMENT '处置时间',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_diff (batch_id, channel, order_no, diff_type),
    KEY idx_tenant_open (tenant_id, handle_status, create_time),
    KEY idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对账差异表';

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'pay_reconcile%';
--   SHOW INDEX FROM pay_reconcile_batch;
--   SHOW INDEX FROM pay_reconcile_diff;
-- =====================================================================
