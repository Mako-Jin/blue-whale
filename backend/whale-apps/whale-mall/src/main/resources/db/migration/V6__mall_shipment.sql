-- =====================================================================
-- Wave 5.98 商城物流与履约：发货单、运单号与签收（whale-mall 侧）
-- ---------------------------------------------------------------------
-- 1) 发货从「订单上的一个状态位」变成独立单据。此前发货只做两件事：
--    订单转 SHIPPED + 扣 reserved 库存。于是没有承运商、没有运单号，
--    买家问「到哪了」只能答「已发货」。
-- 2) 订单与发货单 1:N，支持拆包分批：一个订单三件货、仓库分两个包裹，
--    此前第二次发货会被 OrderStatus.canShip()（只认 PAID）直接拒绝。
-- 3) 行上加 shipped_quantity，与 refunded_quantity 同构：
--    可发数量 = quantity - shipped_quantity，占用走条件更新，
--    两笔发货单并发发同一行时后到的失败，而不是两边都成功、出库才发现只够一份。
-- 4) 一张发货单对应**一个运单号**：运单号是唯一的物理资源，一个包裹一个号。
--    把两个包裹塞进一张单等于允许「一单多号」，tracking_no 从唯一约束退化成备注。
-- 5) carrier_name 落快照：承运商改名或枚举调整后，历史单据仍显示当时的名字。
--
-- 幂等口径：建表 IF NOT EXISTS、ADD COLUMN 前置存在性判断，可在任意已应用 V1–V5 的库上重放。
-- 不回填口径：存量 SHIPPED 订单没有发货单，**故意不回填** shipped_quantity。
--   回填成 quantity 会造出「发满了却没有发货单」的孤儿数据，反而让
--   「作废后订单若已无有效发货单则退回 PAID」的判定错误触发。
--   存量单维持「已发货但无单据」的历史事实，新单起才有单据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 发货单
--    status 三态无草稿态：草稿意味着「占没占可发数量、占没占运单号」要另定规则，
--    而运单号一旦申请就已经是消耗掉的资源。需要「先建单后打印面单」时，
--    属于电子面单对接（本轮不做）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_shipment (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    shipment_no         VARCHAR(32)   NOT NULL                COMMENT '发货单号（MS+yyyyMMdd+6位流水）',
    order_id            BIGINT        NOT NULL                COMMENT '订单ID',
    order_no            VARCHAR(32)   NOT NULL                COMMENT '订单号（快照，便于按单号直查）',
    channel_id          BIGINT        NOT NULL                COMMENT '渠道ID',
    carrier_code        VARCHAR(32)   NOT NULL                COMMENT '承运商编码：SF/YTO/ZTO/STO/YD/JD/EMS/DBL/SELF/OTHER',
    carrier_name        VARCHAR(64)   NOT NULL                COMMENT '承运商名称（快照）',
    tracking_no         VARCHAR(64)   NOT NULL                COMMENT '运单号（必填，自送也填内部单号）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'SHIPPED' COMMENT '状态：SHIPPED 已发货 / DELIVERED 已签收 / CANCELLED 已作废',
    item_count          INT           NOT NULL DEFAULT 0      COMMENT '明细行数',
    total_quantity      INT           NOT NULL DEFAULT 0      COMMENT '本单发货总件数',
    shipped_time        DATETIME      NOT NULL                COMMENT '发货时间',
    delivered_time      DATETIME      DEFAULT NULL            COMMENT '签收时间',
    cancel_time         DATETIME      DEFAULT NULL            COMMENT '作废时间',
    cancel_reason       VARCHAR(255)  DEFAULT NULL            COMMENT '作废原因',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_shipment_no (tenant_id, shipment_no),
    UNIQUE KEY uk_mall_shipment_tracking (tenant_id, carrier_code, tracking_no),
    KEY idx_mall_shipment_order (tenant_id, order_id),
    KEY idx_mall_shipment_status (tenant_id, status, create_time)
) COMMENT='商城发货单';

-- ---------------------------------------------------------------------
-- 2. 发货单明细
--    商品快照与 mall_order_item 同口径：发货单要能独立打印出来，
--    只存 order_item_id 的话，订单行被清理后拣货单就成了空白。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mall_shipment_item (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    shipment_id         BIGINT        NOT NULL                COMMENT '发货单ID',
    order_id            BIGINT        NOT NULL                COMMENT '订单ID',
    order_item_id       BIGINT        NOT NULL                COMMENT '订单明细ID（可发数量按它算）',
    spu_id              BIGINT        NOT NULL                COMMENT '商品ID（快照）',
    sku_id              BIGINT        NOT NULL                COMMENT 'SKU ID（快照）',
    spu_name            VARCHAR(128)  NOT NULL                COMMENT '商品名称（快照）',
    sku_code            VARCHAR(64)   NOT NULL                COMMENT 'SKU 编码（快照）',
    spec_json           VARCHAR(512)  DEFAULT NULL            COMMENT '规格（快照）',
    quantity            INT           NOT NULL                COMMENT '本单发货数量',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mall_shipment_item_ship (tenant_id, shipment_id),
    KEY idx_mall_shipment_item_order_item (tenant_id, order_item_id)
) COMMENT='商城发货单明细';

-- ---------------------------------------------------------------------
-- 3. mall_order_item 追加已发数量
--    默认 0 且不回填：存量 SHIPPED 订单没有发货单，回填会让「无有效发货单则退回 PAID」
--    的判定误判（详见文件头「不回填口径」）。
-- ---------------------------------------------------------------------
SET @has_shipped_quantity = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item' AND COLUMN_NAME = 'shipped_quantity');
SET @ddl = IF(@has_shipped_quantity = 0,
    'ALTER TABLE mall_order_item ADD COLUMN shipped_quantity INT NOT NULL DEFAULT 0 COMMENT ''已发数量（可发 = quantity - shipped_quantity）'' AFTER refunded_quantity',
    'SELECT ''mall_order_item.shipped_quantity 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW CREATE TABLE mall_shipment;
--   SHOW CREATE TABLE mall_shipment_item;
--   SELECT COLUMN_NAME, COLUMN_DEFAULT FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order_item'
--     AND COLUMN_NAME = 'shipped_quantity';
--   -- 重放检查：再次执行本脚本应全部走「已存在，跳过」分支且不报错
-- =====================================================================
