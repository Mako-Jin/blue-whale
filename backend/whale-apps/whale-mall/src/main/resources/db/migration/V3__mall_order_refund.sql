-- =====================================================================
-- Wave 5.95 商城 M1 首切片：订单退款字段（whale-mall 侧）
--   mall_order 追加累计退款金额与退款完成时间；退款状态（REFUNDING/REFUNDED）
--   落在 status VARCHAR 列，无需 DDL。
-- 幂等口径：ADD COLUMN 前置存在性判断，可在任意已应用 V1/V2 的库上重放。
-- =====================================================================

SET @has_refund_amount = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'refund_amount');
SET @ddl = IF(@has_refund_amount = 0,
    'ALTER TABLE mall_order ADD COLUMN refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT ''累计退款金额'' AFTER pay_time',
    'SELECT ''mall_order.refund_amount 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_refund_time = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mall_order' AND COLUMN_NAME = 'refund_time');
SET @ddl = IF(@has_refund_time = 0,
    'ALTER TABLE mall_order ADD COLUMN refund_time DATETIME DEFAULT NULL COMMENT ''退款完成时间'' AFTER refund_amount',
    'SELECT ''mall_order.refund_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM mall_order LIKE 'refund%';
-- =====================================================================
