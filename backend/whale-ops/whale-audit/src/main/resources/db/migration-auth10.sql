-- =====================================================================
-- AUTH-10：审计 MQ 消息去重表（增量迁移，幂等可重跑）
-- =====================================================================
-- 背景：审计记录新增 RabbitMQ 汇聚通道，MQ 为「至少一次」投递语义，
--       消费者重启、网络抖动或死信重放都可能造成重复消息。
-- 用途：以生产者 messageId 为主键去重，与 audit_log 同事务写入；
--       主键冲突即判定为重复消息，跳过落库。
-- 库   ：bluewhale_audit
-- =====================================================================

CREATE TABLE IF NOT EXISTS audit_mq_message (
    message_id  VARCHAR(64) NOT NULL               COMMENT 'MQ 消息ID（生产者生成的 32 位 UUID，幂等去重键）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次消费时间',
    PRIMARY KEY (message_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审计 MQ 消息去重表';
