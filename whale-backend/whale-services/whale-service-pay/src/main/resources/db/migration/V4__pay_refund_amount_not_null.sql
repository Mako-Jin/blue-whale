-- =====================================================================
-- 支付单退款金额列归一化
-- ---------------------------------------------------------------------
-- 历史库或异常写入可能让 pay_order.refund_amount 为 NULL：
--   1) applyRefund 中的 refund_amount + #{amount} 会变成 NULL，条件不成立，
--      退款被误判为「不可退」；
--   2) 概览统计与前端展示也需要稳定的 0 值。
-- 本脚本先把 NULL 回填为 0，再把列收紧为 NOT NULL DEFAULT 0.00。
-- 幂等口径：先查 IS_NULLABLE，已收紧时跳过 ALTER，可在任意已应用 V1~V3 的库上重放。
-- =====================================================================

UPDATE pay_order SET refund_amount = 0.00 WHERE refund_amount IS NULL;

SET @is_nullable = (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND COLUMN_NAME = 'refund_amount');
SET @ddl = IF(@is_nullable = 'YES',
    'ALTER TABLE pay_order MODIFY COLUMN refund_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT ''已退款金额（未退款为 0）''',
    'SELECT ''pay_order.refund_amount 已为 NOT NULL，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM pay_order LIKE 'refund_amount';
--   SELECT COUNT(*) FROM pay_order WHERE refund_amount IS NULL;
-- =====================================================================
