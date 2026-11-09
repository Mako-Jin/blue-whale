-- =====================================================================
-- 商城组合计价与履约增强：活动/会员价 + 商品券 + 重量/区域运费 + WMS 入库
-- ---------------------------------------------------------------------
-- 1) 限时特价活动。mall_activity 按 SKU 配活动价（时间窗 + 启停），
--    下单计价在渠道价目之上取「活动价与渠道价孰低」，活动快照落到订单明细行。
-- 2) 会员价。mall_member 登记会员（有效期内生效）；价目类型新增 MEMBER
--    （零售形态下配置了 MEMBER 价目即按会员价成交），落 mall_member 表
--    由运营维护，价目本身仍在 mall_sku_price（V2）中按一种价目一行扩展。
-- 3) 组合计价快照。mall_order 增加 list_amount（原价合计）与
--    activity_discount（活动+会员价让利合计）；mall_order_item 增加
--    list_price / activity_id / activity_name / price_source / weight_kg。
--    口径：goods_amount = Σ成交价（活动后），券叠加分摊不变；
--    total = goods - coupon_discount + freight 恒等式不受影响。
-- 4) 商品券。mall_coupon / mall_coupon_user 增加 scope=SKU 与 sku_ids
--    （适用 SKU ID，逗号分隔）：门槛与优惠只看命中行，摊也只摊命中行。
--    店铺券沿用既有 CHANNEL 单渠道口径（mall_channel 即店铺主体），不加枚举。
-- 5) 重量/区域运费。mall_sku 增加 weight_kg；mall_freight_template 增加
--    charge_type=BY_WEIGHT（首重续重）与 REGIONAL（区域运费）参数；
--    区域费率独立表 mall_freight_region（模板下按省/区域一行）。
-- 6) WMS 入库联动。mall_stock_inbound（入库单头，doc_no 幂等）+
--    mall_stock_inbound_item（行）：WMS/ERP 经内网接口推送入库，
--    重复推送按 doc_no 幂等返回，不重复加库存。
--
-- 幂等口径：建表 IF NOT EXISTS、ADD COLUMN 前置存在性判断，可重放。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 限时特价活动
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_activity (
    id             BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    activity_code  VARCHAR(64)   NOT NULL                COMMENT '活动编码（租户内唯一）',
    activity_name  VARCHAR(128)  NOT NULL                COMMENT '活动名称',
    channel_id     BIGINT        DEFAULT NULL            COMMENT '适用渠道ID（空 = 全渠道）',
    sku_id         BIGINT        NOT NULL                COMMENT '活动 SKU ID',
    activity_price DECIMAL(10,2) NOT NULL                COMMENT '活动价（元）',
    valid_from     DATETIME      NOT NULL                COMMENT '生效时间',
    valid_to       DATETIME      NOT NULL                COMMENT '失效时间',
    status         VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    remark         VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_activity_code (tenant_id, activity_code),
    KEY idx_mall_activity_sku (tenant_id, sku_id)
) COMMENT='商城限时特价活动';

-- ---------------------------------------------------------------------
-- 2. 会员登记（会员价生效依据）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_member (
    id          BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id     BIGINT       NOT NULL                COMMENT '平台用户ID',
    member_no   VARCHAR(64)  DEFAULT NULL            COMMENT '会员号（可空）',
    level_code  VARCHAR(16)  NOT NULL DEFAULT 'GENERAL' COMMENT '会员等级：GENERAL',
    valid_to    DATETIME     DEFAULT NULL            COMMENT '有效期至（空 = 长期）',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    remark      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_member_user (tenant_id, user_id),
    KEY idx_mall_member_user (tenant_id, user_id, status)
) COMMENT='商城会员登记';

-- ---------------------------------------------------------------------
-- 3. mall_sku 增加重量（kg，按重运费计量的最小快照，下单落到订单行）
-- ---------------------------------------------------------------------
SET @has_weight = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_sku' AND COLUMN_NAME = 'weight_kg');
SET @ddl = IF(@has_weight = 0,
    'ALTER TABLE mall_sku ADD COLUMN weight_kg DECIMAL(10,3) DEFAULT NULL COMMENT ''单件重量（kg，按重运费用）'' AFTER image_url',
    'SELECT ''mall_sku.weight_kg 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. mall_order 增加原价合计与活动让利合计（组合计价对账口径）
-- ---------------------------------------------------------------------
SET @has_list_amount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'list_amount');
SET @ddl = IF(@has_list_amount = 0,
    'ALTER TABLE mall_order ADD COLUMN list_amount DECIMAL(12,2) DEFAULT NULL COMMENT ''原价合计（Σ原价×数量，未含活动与会员让利）'' AFTER goods_amount',
    'SELECT ''mall_order.list_amount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_activity_discount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'activity_discount');
SET @ddl = IF(@has_activity_discount = 0,
    'ALTER TABLE mall_order ADD COLUMN activity_discount DECIMAL(12,2) DEFAULT NULL COMMENT ''活动与会员价让利合计（list_amount - goods_amount）'' AFTER list_amount',
    'SELECT ''mall_order.activity_discount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 5. mall_order_item 增加组合计价快照
-- ---------------------------------------------------------------------
SET @has_list_price = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'list_price');
SET @ddl = IF(@has_list_price = 0,
    'ALTER TABLE mall_order_item ADD COLUMN list_price DECIMAL(10,2) DEFAULT NULL COMMENT ''原单价（成交价覆盖前的价目，快照）'' AFTER unit_price',
    'SELECT ''mall_order_item.list_price 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_item_activity_id = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'activity_id');
SET @ddl = IF(@has_item_activity_id = 0,
    'ALTER TABLE mall_order_item ADD COLUMN activity_id BIGINT DEFAULT NULL COMMENT ''命中活动ID（快照，未命中为空）'' AFTER list_price',
    'SELECT ''mall_order_item.activity_id 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_item_activity_name = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'activity_name');
SET @ddl = IF(@has_item_activity_name = 0,
    'ALTER TABLE mall_order_item ADD COLUMN activity_name VARCHAR(128) DEFAULT NULL COMMENT ''命中活动名称（快照）'' AFTER activity_id',
    'SELECT ''mall_order_item.activity_name 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_price_source = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'price_source');
SET @ddl = IF(@has_price_source = 0,
    'ALTER TABLE mall_order_item ADD COLUMN price_source VARCHAR(16) DEFAULT NULL COMMENT ''成交价来源：LIST 价目 / ACTIVITY 活动 / MEMBER 会员价（快照）'' AFTER activity_name',
    'SELECT ''mall_order_item.price_source 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_item_weight = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'weight_kg');
SET @ddl = IF(@has_item_weight = 0,
    'ALTER TABLE mall_order_item ADD COLUMN weight_kg DECIMAL(10,3) DEFAULT NULL COMMENT ''单件重量kg（快照，按重运费计算依据）'' AFTER price_source',
    'SELECT ''mall_order_item.weight_kg 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 6. mall_freight_template 增加按重计费参数（首重复用 base_amount）
-- ---------------------------------------------------------------------
SET @has_first_weight = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_freight_template' AND COLUMN_NAME = 'first_weight_kg');
SET @ddl = IF(@has_first_weight = 0,
    'ALTER TABLE mall_freight_template ADD COLUMN first_weight_kg DECIMAL(8,2) DEFAULT NULL COMMENT ''首重kg（仅 BY_WEIGHT 用，首重费收 base_amount）'' AFTER piece_free_threshold',
    'SELECT ''mall_freight_template.first_weight_kg 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_continued_weight = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_freight_template' AND COLUMN_NAME = 'continued_weight_kg');
SET @ddl = IF(@has_continued_weight = 0,
    'ALTER TABLE mall_freight_template ADD COLUMN continued_weight_kg DECIMAL(8,2) DEFAULT NULL COMMENT ''续重步长kg（仅 BY_WEIGHT 用，不足一步按一步）'' AFTER first_weight_kg',
    'SELECT ''mall_freight_template.continued_weight_kg 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_continued_amount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_freight_template' AND COLUMN_NAME = 'continued_amount');
SET @ddl = IF(@has_continued_amount = 0,
    'ALTER TABLE mall_freight_template ADD COLUMN continued_amount DECIMAL(12,2) DEFAULT NULL COMMENT ''续重每步运费（仅 BY_WEIGHT 用）'' AFTER continued_weight_kg',
    'SELECT ''mall_freight_template.continued_amount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 7. 区域运费费率（REGIONAL 模板下按省/区域一行；未命中区域回落模板基准）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_freight_region (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    template_id    BIGINT        NOT NULL               COMMENT '运费模板ID',
    region         VARCHAR(64)   NOT NULL               COMMENT '区域（省/直辖市名，模板内唯一）',
    amount         DECIMAL(12,2) NOT NULL               COMMENT '该区域运费',
    free_threshold DECIMAL(12,2) DEFAULT NULL           COMMENT '该区域满额包邮门槛（空 = 不包邮）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_freight_region (tenant_id, template_id, region)
) COMMENT='商城区域运费费率';

-- ---------------------------------------------------------------------
-- 8. 商品券：mall_coupon / mall_coupon_user 增加 sku_ids
-- ---------------------------------------------------------------------
SET @has_coupon_sku_ids = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_coupon' AND COLUMN_NAME = 'sku_ids');
SET @ddl = IF(@has_coupon_sku_ids = 0,
    'ALTER TABLE mall_coupon ADD COLUMN sku_ids VARCHAR(500) DEFAULT NULL COMMENT ''适用SKU ID（逗号分隔，仅 SKU 商品券）'' AFTER category_ids',
    'SELECT ''mall_coupon.sku_ids 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_user_sku_ids = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_coupon_user' AND COLUMN_NAME = 'sku_ids');
SET @ddl = IF(@has_user_sku_ids = 0,
    'ALTER TABLE mall_coupon_user ADD COLUMN sku_ids VARCHAR(500) DEFAULT NULL COMMENT ''适用SKU ID（快照，逗号分隔，仅 SKU 商品券）'' AFTER category_ids',
    'SELECT ''mall_coupon_user.sku_ids 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 9. WMS 入库单（doc_no 幂等：重复推送不再加库存）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_stock_inbound (
    id          BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    doc_no      VARCHAR(64)  NOT NULL               COMMENT '入库单号（租户内唯一，幂等键）',
    source      VARCHAR(32)  NOT NULL DEFAULT 'WMS' COMMENT '来源系统：WMS / ERP / MANUAL',
    line_count  INT          NOT NULL DEFAULT 0     COMMENT '明细行数',
    remark      VARCHAR(255) DEFAULT NULL           COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_stock_inbound_doc (tenant_id, doc_no)
) COMMENT='商城库存入库单（WMS 联动，doc_no 幂等）';

CREATE TABLE IF NOT EXISTS mall_stock_inbound_item (
    id         BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id  BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    inbound_id BIGINT       NOT NULL               COMMENT '入库单ID',
    sku_id     BIGINT       NOT NULL               COMMENT 'SKU ID',
    sku_code   VARCHAR(64)  NOT NULL               COMMENT 'SKU 编码（快照）',
    quantity   INT          NOT NULL               COMMENT '入库数量（正整数）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted    TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mall_stock_inbound_item (tenant_id, inbound_id)
) COMMENT='商城库存入库单明细';

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'mall_activity';
--   SHOW TABLES LIKE 'mall_member';
--   SHOW TABLES LIKE 'mall_freight_region';
--   SHOW TABLES LIKE 'mall_stock_inbound%';
--   SHOW COLUMNS FROM mall_sku LIKE 'weight_kg';
--   SHOW COLUMNS FROM mall_order LIKE 'list_amount';
--   SHOW COLUMNS FROM mall_order_item LIKE 'price_source';
--   SHOW COLUMNS FROM mall_freight_template LIKE '%weight%';
--   SHOW COLUMNS FROM mall_coupon LIKE 'sku_ids';
-- =====================================================================
