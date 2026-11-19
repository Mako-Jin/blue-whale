-- =====================================================================
-- whale-message M1：站内信已读标记列增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_message
-- 执行方式 : mysql -u<user> -p bluewhale_message < migration-5.70-message-inapp.sql
-- 幂等说明 : 先查 information_schema，列不存在才 ALTER，可重复执行。
-- 背景     : M1 站内信收件箱需要按用户维护已读状态，标记记在 msg_delivery 上。
-- =====================================================================

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'msg_delivery'
      AND COLUMN_NAME = 'read_flag'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE msg_delivery ADD COLUMN read_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''站内信已读标记：0 未读 / 1 已读（非 INAPP 渠道恒为 0）'' AFTER sent_at',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
--   FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'msg_delivery' AND COLUMN_NAME = 'read_flag';
-- =====================================================================
