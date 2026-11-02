-- =====================================================================
-- whale-message：系统公告（全员广播）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_message
-- 执行方式 : mysql -u<user> -p bluewhale_message < migration-5.76-message-announcement.sql
-- 幂等说明 : CREATE TABLE IF NOT EXISTS + information_schema 预检，可重复执行。
-- 背景     : 系统公告单条发布、不枚举接收人；用户打开收件箱时惰性展开为
--            msg_delivery（channel=INAPP, recipient=用户ID）个人投递记录，
--            已读状态复用 read_flag。展开以 (message_id, channel, recipient)
--            唯一索引保证幂等，避免并发请求产生重复收件条目。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 系统公告表：标题/正文/业务类型均在关联的 msg_record 上，本表只管范围与生命周期
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_announcement (
    id             BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    record_id      BIGINT       NOT NULL               COMMENT '消息ID（关联 msg_record.id，标题/正文/业务类型在此）',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '公告状态：PUBLISHED 已发布 / REVOKED 已撤回',
    publisher_id   BIGINT       DEFAULT NULL           COMMENT '发布人用户ID',
    publish_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_id (record_id),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_publish_time (publish_time)
) COMMENT='系统公告表';

-- ---------------------------------------------------------------------
-- 2. msg_delivery 历史重复投递记录去重（同 message_id+channel+recipient 保留最小ID）
-- ---------------------------------------------------------------------
DELETE d1 FROM msg_delivery d1
JOIN msg_delivery d2
  ON d1.message_id = d2.message_id
 AND d1.channel    = d2.channel
 AND d1.recipient  = d2.recipient
 AND d1.id > d2.id;

-- ---------------------------------------------------------------------
-- 3. msg_delivery 增加投递唯一索引（展开幂等的并发护栏，不存在才建）
-- ---------------------------------------------------------------------
SET @uk_exists := (
    SELECT COUNT(DISTINCT INDEX_NAME)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'msg_delivery'
      AND INDEX_NAME   = 'uk_message_channel_recipient'
);

SET @ddl := IF(
    @uk_exists = 0,
    'ALTER TABLE msg_delivery ADD UNIQUE KEY uk_message_channel_recipient (message_id, channel, recipient)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW CREATE TABLE msg_announcement;
--   SHOW INDEX FROM msg_delivery WHERE Key_name = 'uk_message_channel_recipient';
-- =====================================================================
