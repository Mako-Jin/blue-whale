-- =====================================================================
-- 三维王城度支殿（T5.3）：支付概览所需的三个索引
-- ---------------------------------------------------------------------
-- 概览每次要问 6 个数：今日收款 / 今日建单 / 待支付与超时 / 今日退款 /
-- 近 7 日趋势 / 今日渠道构成。这些查询的时间窗都落在 pay_time 或 create_time 上，
-- 而 V1 的 pay_order 只有 idx_status、pay_transaction 只有 (tenant_id, order_id)——
-- 数据一多就是全表扫，而总控是 30s 一次的常驻轮询，扫描会被放大成持续负载。
--
-- 三个索引各自对应一类查询：
--   1) pay_order (tenant_id, pay_time)                收款 / 趋势 / 渠道
--   2) pay_order (tenant_id, status, create_time)     待支付与超时未支付
--   3) pay_transaction (tenant_id, type, status, create_time)  失败笔数 / 退款额
--
-- 幂等口径：先查 information_schema.STATISTICS 再决定是否 ADD INDEX，
--           可在任意已应用 V2 的库上重放。
-- =====================================================================

SET @has_pay_time = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND INDEX_NAME = 'idx_tenant_pay_time');
SET @ddl = IF(@has_pay_time = 0,
    'ALTER TABLE pay_order ADD INDEX idx_tenant_pay_time (tenant_id, pay_time)',
    'SELECT ''pay_order.idx_tenant_pay_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_status_time = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND INDEX_NAME = 'idx_tenant_status_time');
SET @ddl = IF(@has_status_time = 0,
    'ALTER TABLE pay_order ADD INDEX idx_tenant_status_time (tenant_id, status, create_time)',
    'SELECT ''pay_order.idx_tenant_status_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tx_time = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_transaction' AND INDEX_NAME = 'idx_tenant_type_time');
SET @ddl = IF(@has_tx_time = 0,
    'ALTER TABLE pay_transaction ADD INDEX idx_tenant_type_time (tenant_id, type, status, create_time)',
    'SELECT ''pay_transaction.idx_tenant_type_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
