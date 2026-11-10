-- =====================================================================
-- Wave 5.96 商城售后单与部分退款（whale-mall 侧）
-- ---------------------------------------------------------------------
-- 1) mall_order_item 追加行级已退数量/已退金额，用于按行占用退款额度。
--    占用走条件更新 `WHERE quantity - refunded_quantity >= ?`，
--    同一行被两张售后单并发申请时后到的直接失败，而不是事后才发现超额。
-- 2) mall_after_sale 售后单：承载「谁申请、退哪几行、审核意见、退款进度」。
--    整单退款降级为它的一条特例（覆盖全部可退行、直接 APPROVED），
--    退款只保留一条实现路径。
-- 3) mall_after_sale_item 售后行：商品信息快照，与 mall_order_item 同理由——
--    商品改名或删除后售后单仍要能独立看懂。
--
-- 幂等口径：ADD COLUMN 前置存在性判断、建表 IF NOT EXISTS，
--           可在任意已应用 V1/V2/V3 的库上重放。
-- 状态口径：售后单 CREATED 待审核 → APPROVED 审核通过（退款执行中）→ REFUNDED 已退款，
--           旁支 REJECTED 已驳回 / CANCELLED 已撤销。
--           订单不新增「部分退款」状态：部分退款只累加 refund_amount，
--           累计达 total_amount 才转 REFUNDED。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. mall_order_item 追加行级退款额度
-- ---------------------------------------------------------------------
SET @has_refunded_qty = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'refunded_quantity');
SET @ddl = IF(@has_refunded_qty = 0,
    'ALTER TABLE mall_order_item ADD COLUMN refunded_quantity INT NOT NULL DEFAULT 0 COMMENT ''已退数量（累计）'' AFTER quantity',
    'SELECT ''mall_order_item.refunded_quantity 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_refunded_amount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'refunded_amount');
SET @ddl = IF(@has_refunded_amount = 0,
    'ALTER TABLE mall_order_item ADD COLUMN refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT ''已退金额（累计）'' AFTER refunded_quantity',
    'SELECT ''mall_order_item.refunded_amount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 售后单
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_after_sale (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    after_sale_no       VARCHAR(64)   NOT NULL                COMMENT '售后单号（租户内唯一，同时作退款幂等键）',
    order_id            BIGINT        NOT NULL                COMMENT '订单ID',
    order_no            VARCHAR(64)   NOT NULL                COMMENT '订单号（冗余存储）',
    user_id             BIGINT        NOT NULL                COMMENT '买家用户ID（校验归属）',
    type                VARCHAR(16)   NOT NULL                COMMENT '类型：REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款',
    status              VARCHAR(16)   NOT NULL DEFAULT 'CREATED'
                                                             COMMENT '状态：CREATED 待审核 / APPROVED 审核通过 / REFUNDED 已退款 / REJECTED 已驳回 / CANCELLED 已撤销',
    refund_amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '本次申请退款金额（服务端按行计算）',
    reason              VARCHAR(255)  DEFAULT NULL            COMMENT '售后原因',
    description         VARCHAR(500)  DEFAULT NULL            COMMENT '问题描述',
    images              VARCHAR(1000) DEFAULT NULL            COMMENT '凭证图片URL（逗号分隔）',
    audit_user_id       BIGINT        DEFAULT NULL            COMMENT '审核人ID',
    audit_user_name     VARCHAR(64)   DEFAULT NULL            COMMENT '审核人名称（快照）',
    audit_time          DATETIME      DEFAULT NULL            COMMENT '审核时间',
    audit_remark        VARCHAR(255)  DEFAULT NULL            COMMENT '审核意见',
    refund_no           VARCHAR(64)   DEFAULT NULL            COMMENT '支付侧退款流水号（回调回填，仅追溯）',
    refund_time         DATETIME      DEFAULT NULL            COMMENT '退款完成时间',
    return_logistics_no VARCHAR(64)   DEFAULT NULL            COMMENT '退回物流单号（退货退款用，本切片仅留存）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_after_sale_no (tenant_id, after_sale_no),
    KEY idx_mall_after_sale_order (tenant_id, order_id),
    KEY idx_mall_after_sale_status (tenant_id, status),
    KEY idx_mall_after_sale_user (tenant_id, user_id)
) COMMENT='商城售后单';

-- ---------------------------------------------------------------------
-- 3. 售后行（商品快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_after_sale_item (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    after_sale_id       BIGINT        NOT NULL                COMMENT '售后单ID',
    order_item_id       BIGINT        NOT NULL                COMMENT '订单明细ID（行级额度占用的目标）',
    spu_id              BIGINT        NOT NULL                COMMENT '商品ID',
    sku_id              BIGINT        NOT NULL                COMMENT 'SKU ID',
    spu_name            VARCHAR(128)  NOT NULL                COMMENT '商品名称（快照）',
    sku_code            VARCHAR(64)   NOT NULL                COMMENT 'SKU 编码（快照）',
    unit_price          DECIMAL(12,2) NOT NULL                COMMENT '成交单价（快照）',
    quantity            INT           NOT NULL                COMMENT '本次退数量',
    amount              DECIMAL(12,2) NOT NULL                COMMENT '本次退金额 = 单价 × 数量',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mall_after_sale_item_as (tenant_id, after_sale_id),
    KEY idx_mall_after_sale_item_order_item (tenant_id, order_item_id)
) COMMENT='商城售后明细（商品快照）';

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM mall_order_item LIKE 'refunded%';
--   SHOW TABLES LIKE 'mall_after_sale%';
-- =====================================================================
