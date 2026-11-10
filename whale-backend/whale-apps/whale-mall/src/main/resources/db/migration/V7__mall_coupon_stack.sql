-- =====================================================================
-- 商城计价与营销增强：券叠加、品类券、按件运费、日报表口径（whale-mall 侧）
-- ---------------------------------------------------------------------
-- 1) 券叠加。此前一单只能用一张券（mall_order.coupon_user_id 单列承载）。
--    本迁移引入明细表 mall_order_coupon：一单最多 2 张，折扣券最多 1 张，
--    门槛按各自适用范围商品小计判定（全场/渠道券看整单，品类券看命中类目行），
--    优惠只摊到命中行（share_discount 累加）。
--    兼容口径：mall_order.coupon_user_id / coupon_code 保留，含义收窄为
--    「首张券（主券）快照」——单券订单与全部老数据语义完全一致；
--    多券明细以 mall_order_coupon 为准（按订单号退券走 mall_coupon_user.order_no，
--    天然覆盖多张）。
-- 2) 品类券。mall_coupon / mall_coupon_user 增加 scope（ALL 全场 / CHANNEL 单渠道 /
--    CATEGORY 品类）与 category_ids（适用类目 ID，逗号分隔）。
--    用户券落 scope 与类目快照：模板改类目时，已领出去的券不能跟着变。
--    存量回填：channel_id 非空的老券视为 CHANNEL。
-- 3) 按件运费。mall_freight_template 增加 charge_type=BY_PIECE 的两个参数：
--    piece_price（每件运费）、piece_free_threshold（包邮件数门槛，可空）。
--    按重量、首重续重、区域运费仍不做（需要商品级模板与地址库，是独立一波的量）。
-- 4) 日报表口径。直接聚合 mall_order（status IN 已收款未取消 states），
--    按日汇总 goods_amount / freight_amount / discount_amount / total_amount /
--    refund_amount 与订单数，不建快照表。
--
-- 幂等口径：建表 IF NOT EXISTS、ADD COLUMN 前置存在性判断、回填条件自带幂等，
--           可在任意已应用 V1–V6 的库上重放。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 订单用券明细（一单多券）
--    discount_amount 是该券在本单最终生效的优惠额（封顶与按比例缩减之后），
--    scope / category_ids 是券的适用范围快照，category_ids 逗号分隔。
--    order_no 冗余落一份，按单对账与退券不用先查订单头。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_order_coupon (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    order_id        BIGINT        NOT NULL                COMMENT '订单ID',
    order_no        VARCHAR(64)   NOT NULL                COMMENT '订单号（冗余，便于按单对账）',
    coupon_user_id  BIGINT        NOT NULL                COMMENT '用户券ID',
    coupon_id       BIGINT        NOT NULL                COMMENT '券模板ID',
    coupon_code     VARCHAR(64)   NOT NULL                COMMENT '券编码（快照）',
    coupon_name     VARCHAR(128)  NOT NULL                COMMENT '券名称（快照）',
    coupon_type     VARCHAR(16)   NOT NULL                COMMENT '券类型（快照）：FULL_REDUCTION / DISCOUNT',
    scope           VARCHAR(16)   NOT NULL DEFAULT 'ALL'  COMMENT '适用范围（快照）：ALL 全场 / CHANNEL 单渠道 / CATEGORY 品类',
    category_ids    VARCHAR(500)  DEFAULT NULL            COMMENT '适用类目ID（快照，逗号分隔，仅 CATEGORY）',
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '该券本单生效优惠额',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_order_coupon (tenant_id, order_id, coupon_user_id),
    KEY idx_mall_order_coupon_order (tenant_id, order_no)
) COMMENT='商城订单用券明细（一单多券）';

-- ---------------------------------------------------------------------
-- 2. mall_order_item 追加类目快照（品类券判定用）
--    落下单当时的类目 ID：商品事后改类目，历史订单的品类券分摊不能跟着变。
-- ---------------------------------------------------------------------
SET @has_category_id = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'category_id');
SET @ddl = IF(@has_category_id = 0,
    'ALTER TABLE mall_order_item ADD COLUMN category_id BIGINT DEFAULT NULL COMMENT ''商品类目ID（快照，品类券判定用）'' AFTER sku_id',
    'SELECT ''mall_order_item.category_id 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. mall_coupon 追加适用范围
-- ---------------------------------------------------------------------
SET @has_scope = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_coupon' AND COLUMN_NAME = 'scope');
SET @ddl = IF(@has_scope = 0,
    'ALTER TABLE mall_coupon ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT ''ALL'' COMMENT ''适用范围：ALL 全场 / CHANNEL 单渠道 / CATEGORY 品类'' AFTER channel_id',
    'SELECT ''mall_coupon.scope 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_coupon_category_ids = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_coupon' AND COLUMN_NAME = 'category_ids');
SET @ddl = IF(@has_coupon_category_ids = 0,
    'ALTER TABLE mall_coupon ADD COLUMN category_ids VARCHAR(500) DEFAULT NULL COMMENT ''适用类目ID（逗号分隔，仅 CATEGORY）'' AFTER scope',
    'SELECT ''mall_coupon.category_ids 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量回填：channel_id 非空的老券按单渠道券理解。条件自带幂等，重复执行不再改值。
UPDATE mall_coupon
   SET scope = 'CHANNEL'
 WHERE channel_id IS NOT NULL
   AND scope = 'ALL';

-- ---------------------------------------------------------------------
-- 4. mall_coupon_user 追加适用范围快照（同模板口径）
-- ---------------------------------------------------------------------
SET @has_user_scope = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_coupon_user' AND COLUMN_NAME = 'scope');
SET @ddl = IF(@has_user_scope = 0,
    'ALTER TABLE mall_coupon_user ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT ''ALL'' COMMENT ''适用范围（快照）：ALL 全场 / CHANNEL 单渠道 / CATEGORY 品类'' AFTER channel_id',
    'SELECT ''mall_coupon_user.scope 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_user_category_ids = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_coupon_user' AND COLUMN_NAME = 'category_ids');
SET @ddl = IF(@has_user_category_ids = 0,
    'ALTER TABLE mall_coupon_user ADD COLUMN category_ids VARCHAR(500) DEFAULT NULL COMMENT ''适用类目ID（快照，逗号分隔，仅 CATEGORY）'' AFTER scope',
    'SELECT ''mall_coupon_user.category_ids 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量回填：与模板同口径，channel_id 非空的老券按单渠道券理解。
UPDATE mall_coupon_user
   SET scope = 'CHANNEL'
 WHERE channel_id IS NOT NULL
   AND scope = 'ALL';

-- ---------------------------------------------------------------------
-- 5. mall_freight_template 追加按件计费参数
--    piece_price 每件运费；piece_free_threshold 包邮件数门槛（可空 = 不包邮）。
-- ---------------------------------------------------------------------
SET @has_piece_price = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_freight_template' AND COLUMN_NAME = 'piece_price');
SET @ddl = IF(@has_piece_price = 0,
    'ALTER TABLE mall_freight_template ADD COLUMN piece_price DECIMAL(12,2) DEFAULT NULL COMMENT ''每件运费（仅 BY_PIECE 用）'' AFTER free_threshold',
    'SELECT ''mall_freight_template.piece_price 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_piece_free_threshold = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_freight_template' AND COLUMN_NAME = 'piece_free_threshold');
SET @ddl = IF(@has_piece_free_threshold = 0,
    'ALTER TABLE mall_freight_template ADD COLUMN piece_free_threshold INT DEFAULT NULL COMMENT ''包邮件数门槛（仅 BY_PIECE 可选，达到则免运费）'' AFTER piece_price',
    'SELECT ''mall_freight_template.piece_free_threshold 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'mall_order_coupon';
--   SHOW COLUMNS FROM mall_order_item LIKE 'category_id';
--   SHOW COLUMNS FROM mall_coupon LIKE 'scope';
--   SHOW COLUMNS FROM mall_coupon_user LIKE 'scope';
--   SHOW COLUMNS FROM mall_freight_template LIKE 'piece%';
--   SELECT COUNT(*) FROM mall_coupon WHERE channel_id IS NOT NULL AND scope = 'ALL';  -- 应为 0
-- =====================================================================
