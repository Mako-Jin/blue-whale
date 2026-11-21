-- =====================================================================
-- 三维王城通政殿（T5.8）：消息投递概览所需的四个索引
-- ---------------------------------------------------------------------
-- 概览每次要问 5 个数：今日投递 / 近 24h 成败与退信 / 积压未发 / 近 7 日趋势 /
-- 失败渠道 Top。它们的形状只有三种：
--   ① 「本租户 + 投递时间窗」（msg_delivery.sent_at）
--   ② 「本租户 + 状态 + 投递时间窗」（msg_delivery 的失败 Top、退信）
--   ③ 「本租户 + 状态 + 创建时间早于阈值」（msg_record 的积压未发）
-- 而 V1 的 msg_delivery 只有 idx_tenant_message / idx_tenant_status /
-- idx_tenant_provider / idx_create_time，没有一个能同时吃下"租户 + sent_at"——
-- MySQL 只能选一个单列索引用，另一个条件退化成回表过滤。总控是 30s 一次的常驻轮询，
-- 这种退化会被放大成持续负载。
--
-- 四个索引各自对应一类查询：
--   1) msg_delivery (tenant_id, sent_at)              今日投递 / 按天趋势
--   2) msg_delivery (tenant_id, status, sent_at)      成败统计 / 失败渠道 Top / 退信
--   3) msg_record   (tenant_id, status, create_time)  积压未发（带租户，概览与后台共用）
--   4) msg_record   (status, create_time)             积压未发（跨租户，补偿任务无租户上下文）
--
-- idx_tenant_status 被 2) 最左前缀完全覆盖，一并用它替换：留着只会让每次插入多维护一份。
-- 3) 与 4) 不能互相替代——补偿任务查的是"所有租户里卡住的 PENDING"，没有 tenant_id，
-- 带租户前缀的索引用不上；而概览必须带租户，否则会把别家的积压算进来。
--
-- 幂等口径：先查 information_schema.STATISTICS 再决定是否 ADD INDEX，
--           可在任意已应用 V8 的库上重放。
-- =====================================================================

SET @has_tenant_sent = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'msg_delivery' AND INDEX_NAME = 'idx_tenant_sent');
SET @ddl = IF(@has_tenant_sent = 0,
    'ALTER TABLE msg_delivery ADD INDEX idx_tenant_sent (tenant_id, sent_at)',
    'SELECT ''msg_delivery.idx_tenant_sent 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_status_sent = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'msg_delivery' AND INDEX_NAME = 'idx_tenant_status_sent');
SET @ddl = IF(@has_tenant_status_sent = 0,
    'ALTER TABLE msg_delivery ADD INDEX idx_tenant_status_sent (tenant_id, status, sent_at)',
    'SELECT ''msg_delivery.idx_tenant_status_sent 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_status_create = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'msg_record' AND INDEX_NAME = 'idx_tenant_status_create');
SET @ddl = IF(@has_tenant_status_create = 0,
    'ALTER TABLE msg_record ADD INDEX idx_tenant_status_create (tenant_id, status, create_time)',
    'SELECT ''msg_record.idx_tenant_status_create 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_status_create = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'msg_record' AND INDEX_NAME = 'idx_status_create');
SET @ddl = IF(@has_status_create = 0,
    'ALTER TABLE msg_record ADD INDEX idx_status_create (status, create_time)',
    'SELECT ''msg_record.idx_status_create 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 冗余单列索引：只有在新复合索引确实存在时才删，避免在只跑了一半的库上
-- 把 msg_delivery 唯一的"租户 + 状态"索引删掉
SET @has_status_sent_ready = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'msg_delivery' AND INDEX_NAME = 'idx_tenant_status_sent');
SET @has_status_only = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'msg_delivery' AND INDEX_NAME = 'idx_tenant_status');
SET @ddl = IF(@has_status_sent_ready > 0 AND @has_status_only > 0,
    'ALTER TABLE msg_delivery DROP INDEX idx_tenant_status',
    'SELECT ''msg_delivery.idx_tenant_status 无需删除（不存在或复合索引未就绪），跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
