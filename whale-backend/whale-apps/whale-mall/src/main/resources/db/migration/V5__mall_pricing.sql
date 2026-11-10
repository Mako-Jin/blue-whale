-- =====================================================================
-- Wave 5.97 商城计价重构：优惠券与运费（whale-mall 侧）
-- ---------------------------------------------------------------------
-- 1) 金额构成拆解。此前订单只有一个 total_amount，语义是「商品行小计之和」，
--    但它同时被支付单、退款额度、报表成交额、财务对账四拨人使用，各按各的理解。
--    本迁移把它拆成四项，total_amount 语义收窄为「应付」：
--        goods_amount    商品总额 = Σ 行 amount
--        discount_amount 优惠金额（券）
--        freight_amount  运费
--        total_amount    应付 = goods_amount - discount_amount + freight_amount
--    total_amount 不改值、不改名，支付单金额与退款额度继续读它，
--    因此老订单天然自洽（无券无运费时 goods = total）。
-- 2) 优惠与运费分摊到行（share_discount / share_freight）。
--    不分摊则「退这一件该退多少钱」无解：订单 100 用 20 元券、退其中 50 元那行，
--    退 40 还是 50 取决于算法；分摊之后答案唯一且可审计。
-- 3) freight_template_id 挂在渠道上：本切片只做渠道级运费（固定/免/满额包邮），
--    按件数/重量/多模板累加是独立一波的量。
-- 4) mall_coupon 是券模板、mall_coupon_user 是用户领到的券实例。
--    分两张表且实例落快照：运营把「满 100 减 50」改成「满 100 减 5」时，
--    已经领出去的券不能跟着变——那是发出去的承诺。
--
-- 幂等口径：建表 IF NOT EXISTS、ADD COLUMN 前置存在性判断，
--           可在任意已应用 V1–V4 的库上重放。
-- 回填口径：goods_amount 为 0 且 total_amount 大于 0 时按 total_amount 补齐，
--           条件自带幂等，重复执行不改变已回填的值。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 运费模板
--    charge_type 三选一：FREE 全免 / FIXED 固定 / THRESHOLD 满额包邮。
--    满额判定的分母是 goods_amount（含优惠前）：用券后跌破包邮线就收运费，
--    会让「领了 20 元券反而多付 12 元运费」，是最容易被投诉的交互。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_freight_template (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    template_name       VARCHAR(64)   NOT NULL                COMMENT '模板名称（租户内唯一）',
    charge_type         VARCHAR(16)   NOT NULL                COMMENT '计费方式：FREE 全免 / FIXED 固定 / THRESHOLD 满额包邮',
    base_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '固定运费金额（FREE 时忽略）',
    free_threshold      DECIMAL(12,2) DEFAULT NULL            COMMENT '包邮门槛（仅 THRESHOLD 用）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_freight_name (tenant_id, template_name),
    KEY idx_mall_freight_status (tenant_id, status)
) COMMENT='商城运费模板（渠道级）';

-- ---------------------------------------------------------------------
-- 2. 优惠券模板
--    FULL_REDUCTION 用 face_amount（满减面额），DISCOUNT 用 discount_rate（如 0.8500 = 85 折）。
--    折扣券的 max_discount_amount 是必填项（业务层校验）：不封顶的 8 折券
--    在 10 万元订单上算出 2 万元优惠，运营只能事后追认。
--    channel_id 为空表示全场通用；本切片不做品类券/商品券。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_coupon (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    coupon_code         VARCHAR(64)   NOT NULL                COMMENT '券编码（租户内唯一）',
    coupon_name         VARCHAR(128)  NOT NULL                COMMENT '券名称',
    coupon_type         VARCHAR(16)   NOT NULL                COMMENT '类型：FULL_REDUCTION 满减 / DISCOUNT 折扣',
    threshold_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '使用门槛（商品总额满多少可用，0 = 无门槛）',
    face_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '满减面额（FULL_REDUCTION 用）',
    discount_rate       DECIMAL(5,4)  DEFAULT NULL            COMMENT '折扣率（DISCOUNT 用，0.8500 表示 85 折）',
    max_discount_amount DECIMAL(12,2) DEFAULT NULL            COMMENT '折扣封顶金额（DISCOUNT 必填）',
    channel_id          BIGINT        DEFAULT NULL            COMMENT '适用渠道ID（NULL = 全场通用）',
    total_qty           INT           NOT NULL DEFAULT 0      COMMENT '发行量（0 = 不限）',
    issued_qty          INT           NOT NULL DEFAULT 0      COMMENT '已发行数量',
    per_user_limit      INT           NOT NULL DEFAULT 1      COMMENT '每人限领张数',
    valid_from          DATETIME      NOT NULL                COMMENT '生效时间',
    valid_to            DATETIME      NOT NULL                COMMENT '失效时间',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_coupon_code (tenant_id, coupon_code),
    KEY idx_mall_coupon_status (tenant_id, status, valid_to)
) COMMENT='商城优惠券模板';

-- ---------------------------------------------------------------------
-- 3. 用户券
--    面额/门槛/折扣/有效期/渠道全部落快照，不靠 coupon_id 反查模板——
--    模板一改，历史承诺就变了。
--    claim_seq 是「该用户持有的第几张」，与 coupon_id + user_id 组成唯一键：
--    并发领同一张券时后到的撞键失败，而 count 校验在并发下挡不住
--    （两个请求同时读到 0 张、同时插入）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_coupon_user (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    coupon_id           BIGINT        NOT NULL                COMMENT '券模板ID',
    user_id             BIGINT        NOT NULL                COMMENT '持有人用户ID',
    claim_seq           INT           NOT NULL DEFAULT 1      COMMENT '该用户领取的第几张（1 起）',
    coupon_code         VARCHAR(64)   NOT NULL                COMMENT '券编码（快照）',
    coupon_name         VARCHAR(128)  NOT NULL                COMMENT '券名称（快照）',
    coupon_type         VARCHAR(16)   NOT NULL                COMMENT '类型（快照）',
    threshold_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '使用门槛（快照）',
    face_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '满减面额（快照）',
    discount_rate       DECIMAL(5,4)  DEFAULT NULL            COMMENT '折扣率（快照）',
    max_discount_amount DECIMAL(12,2) DEFAULT NULL            COMMENT '折扣封顶（快照）',
    channel_id          BIGINT        DEFAULT NULL            COMMENT '适用渠道（快照，NULL = 全场）',
    valid_from          DATETIME      NOT NULL                COMMENT '生效时间（快照）',
    valid_to            DATETIME      NOT NULL                COMMENT '失效时间（快照）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'UNUSED'
                                                             COMMENT '状态：UNUSED 未使用 / USED 已使用 / EXPIRED 已过期',
    order_no            VARCHAR(32)   DEFAULT NULL            COMMENT '核销订单号',
    used_time           DATETIME      DEFAULT NULL            COMMENT '核销时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_coupon_user (tenant_id, coupon_id, user_id, claim_seq),
    KEY idx_mall_coupon_user_mine (tenant_id, user_id, status, valid_to),
    KEY idx_mall_coupon_user_order (tenant_id, order_no)
) COMMENT='商城用户券（领取实例）';

-- ---------------------------------------------------------------------
-- 4. mall_order 追加金额构成与券快照
-- ---------------------------------------------------------------------
SET @has_goods_amount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'goods_amount');
SET @ddl = IF(@has_goods_amount = 0,
    'ALTER TABLE mall_order ADD COLUMN goods_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT ''商品总额（行小计之和，不含优惠与运费）'' AFTER total_amount',
    'SELECT ''mall_order.goods_amount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_discount_amount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'discount_amount');
SET @ddl = IF(@has_discount_amount = 0,
    'ALTER TABLE mall_order ADD COLUMN discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT ''优惠金额（券）'' AFTER goods_amount',
    'SELECT ''mall_order.discount_amount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_freight_amount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'freight_amount');
SET @ddl = IF(@has_freight_amount = 0,
    'ALTER TABLE mall_order ADD COLUMN freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT ''运费'' AFTER discount_amount',
    'SELECT ''mall_order.freight_amount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_coupon_user_id = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'coupon_user_id');
SET @ddl = IF(@has_coupon_user_id = 0,
    'ALTER TABLE mall_order ADD COLUMN coupon_user_id BIGINT DEFAULT NULL COMMENT ''使用的用户券ID（快照）'' AFTER freight_amount',
    'SELECT ''mall_order.coupon_user_id 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_coupon_code = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'coupon_code');
SET @ddl = IF(@has_coupon_code = 0,
    'ALTER TABLE mall_order ADD COLUMN coupon_code VARCHAR(64) DEFAULT NULL COMMENT ''使用的券编码（快照）'' AFTER coupon_user_id',
    'SELECT ''mall_order.coupon_code 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 老订单回填：无优惠无运费时商品总额等于原 total_amount。
-- 条件 goods_amount = 0 自带幂等，重复执行不会改写已回填的值。
UPDATE mall_order
   SET goods_amount = total_amount
 WHERE goods_amount = 0.00
   AND total_amount > 0.00;

-- ---------------------------------------------------------------------
-- 5. mall_order_item 追加分摊字段
--    行实付额 = amount - share_discount + share_freight，
--    售后可退上限以它为准（沿用 amount 会让折扣订单按原价退，商家倒贴差价）。
-- ---------------------------------------------------------------------
SET @has_share_discount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'share_discount');
SET @ddl = IF(@has_share_discount = 0,
    'ALTER TABLE mall_order_item ADD COLUMN share_discount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT ''分摊优惠金额'' AFTER amount',
    'SELECT ''mall_order_item.share_discount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_share_freight = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'share_freight');
SET @ddl = IF(@has_share_freight = 0,
    'ALTER TABLE mall_order_item ADD COLUMN share_freight DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT ''分摊运费'' AFTER share_discount',
    'SELECT ''mall_order_item.share_freight 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 6. mall_channel 追加运费模板绑定（NULL = 不收运费）
-- ---------------------------------------------------------------------
SET @has_freight_template = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_channel' AND COLUMN_NAME = 'freight_template_id');
SET @ddl = IF(@has_freight_template = 0,
    'ALTER TABLE mall_channel ADD COLUMN freight_template_id BIGINT DEFAULT NULL COMMENT ''运费模板ID（空 = 不收运费）'' AFTER currency_type',
    'SELECT ''mall_channel.freight_template_id 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'mall_freight_template%';
--   SHOW TABLES LIKE 'mall_coupon%';
--   SHOW COLUMNS FROM mall_order LIKE '%amount%';
--   SHOW COLUMNS FROM mall_order_item LIKE 'share%';
--   SELECT COUNT(*) FROM mall_order WHERE goods_amount = 0.00 AND total_amount > 0.00;  -- 应为 0
-- =====================================================================
