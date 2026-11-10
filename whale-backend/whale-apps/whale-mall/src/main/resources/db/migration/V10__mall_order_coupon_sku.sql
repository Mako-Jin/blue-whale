-- V10: 订单用券明细补 SKU 范围快照（商品券分摊对账需要行级绑定）
-- V9 建表时只扩了 mall_coupon / mall_coupon_user，落漏了 mall_order_coupon；
-- 已应用过 V9 的环境靠本迁移补齐，新环境 V9+V10 顺序执行同样幂等。

SET @ddl := (SELECT IF(
        (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'mall_order_coupon'
           AND column_name = 'sku_ids') = 0,
    'ALTER TABLE mall_order_coupon ADD COLUMN sku_ids VARCHAR(500) NULL AFTER category_ids',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
