-- =====================================================================
-- 阶段 5：支付超时自动关单 + 通知/对账调度单实例锁
-- ---------------------------------------------------------------------
-- 1) pay_order 追加关单原因、关单失败重试计数与下次重试时间：
--    close_reason   TIMEOUT / MANUAL / CHANNEL_CLOSED
--    close_attempts 渠道关单失败次数（复用通知任务的指数退避模型）
--    next_close_at  下次允许重试关单的时刻（扫描时过滤，避免热重试）
-- 2) 关单扫描索引 (status, create_time)：定时线程跨租户扫描 CREATED 超时单
-- 3) shedlock 表：通知重试与日切对账共用，避免多实例重复执行
-- 幂等口径：ADD COLUMN / ADD INDEX 先查 information_schema，可重复执行。
-- =====================================================================

SET @has_close_reason = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND COLUMN_NAME = 'close_reason');
SET @ddl = IF(@has_close_reason = 0,
    'ALTER TABLE pay_order ADD COLUMN close_reason VARCHAR(32) DEFAULT NULL COMMENT ''关单原因：TIMEOUT 超时 / MANUAL 人工 / CHANNEL_CLOSED 渠道关单'' AFTER status',
    'SELECT ''pay_order.close_reason 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_close_attempts = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND COLUMN_NAME = 'close_attempts');
SET @ddl = IF(@has_close_attempts = 0,
    'ALTER TABLE pay_order ADD COLUMN close_attempts INT NOT NULL DEFAULT 0 COMMENT ''渠道关单失败次数（指数退避）'' AFTER close_reason',
    'SELECT ''pay_order.close_attempts 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_next_close_at = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND COLUMN_NAME = 'next_close_at');
SET @ddl = IF(@has_next_close_at = 0,
    'ALTER TABLE pay_order ADD COLUMN next_close_at DATETIME DEFAULT NULL COMMENT ''下次允许重试关单的时刻'' AFTER close_attempts',
    'SELECT ''pay_order.next_close_at 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_close_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pay_order' AND INDEX_NAME = 'idx_status_create_time');
SET @ddl = IF(@has_close_index = 0,
    'ALTER TABLE pay_order ADD INDEX idx_status_create_time (status, create_time)',
    'SELECT ''pay_order.idx_status_create_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL COMMENT '锁名称',
    lock_until TIMESTAMP(3) NOT NULL COMMENT '锁到期时间',
    locked_at  TIMESTAMP(3) NOT NULL COMMENT '加锁时间',
    locked_by  VARCHAR(255) NOT NULL COMMENT '持有者（实例标识）',
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务分布式锁';

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM pay_order LIKE 'close_%';
--   SHOW TABLES LIKE 'shedlock';
-- =====================================================================
