-- =====================================================================
-- V2: 送达回执能力（DELIVERED / READ / BOUNCED）
-- =====================================================================
-- 背景   : V1 只有「提交态」（SENT / FAILED / SKIPPED / MOCKED），渠道侧确认送达
--          与接收人阅读的结果无法回流，业务方只能看到「已交给渠道」。
-- 变更   : 1) msg_record 记录业务方回调地址；
--          2) msg_delivery 增加送达/阅读时间，状态位扩展为
--             SENT / DELIVERED / READ / BOUNCED / FAILED / SKIPPED / MOCKED；
--          3) 新增 msg_receipt 回执流水（幂等键去重，保留原始报文）；
--          4) 新增 msg_callback_task 回调 outbox（失败重试，避免同步阻塞回执上报）。
-- 兼容   : 全部为新增列/新增表，历史数据保持 NULL / 默认值，可安全重复部署。
-- =====================================================================

ALTER TABLE `msg_record`
    ADD COLUMN `callback_url` VARCHAR(512) DEFAULT NULL COMMENT '回执回调地址（业务方内网端点；空表示不回传）' AFTER `status`;

ALTER TABLE `msg_delivery`
    ADD COLUMN `delivered_at` DATETIME DEFAULT NULL COMMENT '渠道确认送达时间' AFTER `sent_at`,
    ADD COLUMN `read_at`      DATETIME DEFAULT NULL COMMENT '接收人阅读时间（仅 INAPP / IM 等可回传阅读态的渠道）' AFTER `delivered_at`,
    ADD KEY `idx_tenant_provider` (`tenant_id`, `channel`, `provider_message_id`);

CREATE TABLE IF NOT EXISTS msg_receipt (
    id                  BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    receipt_key         VARCHAR(160) NOT NULL                COMMENT '回执幂等键：渠道:回执ID（渠道未给ID时由渠道+提供方消息ID+状态+时间派生）',
    channel             VARCHAR(16)  NOT NULL                COMMENT '渠道：INAPP / EMAIL / SMS / IM',
    provider_message_id VARCHAR(128) DEFAULT NULL            COMMENT '渠道侧消息标识（用于匹配 msg_delivery.provider_message_id）',
    delivery_id         BIGINT       DEFAULT NULL            COMMENT '匹配到的投递记录ID（未匹配为空）',
    message_id          BIGINT       DEFAULT NULL            COMMENT '匹配到的消息ID（未匹配为空）',
    receipt_status      VARCHAR(16)  NOT NULL                COMMENT '回执状态：DELIVERED / READ / BOUNCED / UNMATCHED',
    match_status        VARCHAR(16)  NOT NULL DEFAULT 'MATCHED' COMMENT '匹配结果：MATCHED / UNMATCHED',
    receipt_time        DATETIME     DEFAULT NULL            COMMENT '渠道回执时间',
    error_code          VARCHAR(64)  DEFAULT NULL            COMMENT '返信错误码（退信/失败时）',
    error_message       VARCHAR(512) DEFAULT NULL            COMMENT '返信错误说明',
    raw_payload         TEXT         DEFAULT NULL            COMMENT '渠道原始回执报文（截断保留）',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_receipt_key (tenant_id, receipt_key),
    KEY idx_tenant_delivery (tenant_id, delivery_id),
    KEY idx_tenant_provider_msg (tenant_id, channel, provider_message_id),
    KEY idx_create_time (create_time)
) COMMENT='消息送达回执流水表';

CREATE TABLE IF NOT EXISTS msg_callback_task (
    id            BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    message_id    BIGINT       NOT NULL                COMMENT '消息ID',
    delivery_id   BIGINT       DEFAULT NULL            COMMENT '投递记录ID',
    receipt_id    BIGINT       NOT NULL                COMMENT '触发本回调的回执流水ID',
    callback_url  VARCHAR(512) NOT NULL                COMMENT '回调地址',
    payload_json  TEXT         DEFAULT NULL            COMMENT '回调报文 JSON',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '回调状态：PENDING / SENT / FAILED / GAVE_UP',
    attempts      INT          NOT NULL DEFAULT 0      COMMENT '已尝试次数',
    next_retry_at DATETIME     DEFAULT NULL            COMMENT '下次重试时间',
    last_error    VARCHAR(512) DEFAULT NULL            COMMENT '最近一次失败原因',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_receipt_delivery (tenant_id, receipt_id, delivery_id),
    KEY idx_status_retry (status, next_retry_at),
    KEY idx_tenant_message (tenant_id, message_id)
) COMMENT='消息回执回调任务表（outbox）';
