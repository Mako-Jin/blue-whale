-- V14: 触达回执闭环——明细承接渠道回执状态，任务侧补送达/退信/已读计数
-- 语义：SENT 为"已提交渠道"，DELIVERED / READ / BOUNCED 由 whale-message 回执驱动，
--       单向推进（只前进不回退），回执迟到或乱序不会把已读打回已送达。

ALTER TABLE `crm_marketing_send`
    ADD COLUMN `delivered_count` INT NOT NULL DEFAULT 0 COMMENT '已送达数量（渠道回执聚合）' AFTER `failed_count`,
    ADD COLUMN `bounced_count`   INT NOT NULL DEFAULT 0 COMMENT '退信数量（渠道回执聚合）' AFTER `delivered_count`,
    ADD COLUMN `read_count`      INT NOT NULL DEFAULT 0 COMMENT '已读数量（渠道回执聚合）' AFTER `bounced_count`;

ALTER TABLE `crm_marketing_send_item`
    ADD COLUMN `msg_id`       BIGINT   DEFAULT NULL COMMENT '消息服务消息ID（回执匹配键）' AFTER `status`,
    ADD COLUMN `delivered_at` DATETIME DEFAULT NULL COMMENT '渠道确认送达时间' AFTER `msg_id`,
    ADD COLUMN `read_flag`    TINYINT  NOT NULL DEFAULT 0 COMMENT '已读标记：0 未读 / 1 已读' AFTER `delivered_at`,
    MODIFY COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT '明细状态：PENDING / SENT / DELIVERED / READ / BOUNCED / SKIPPED / FAILED',
    ADD KEY `idx_crm_send_item_msg` (`tenant_id`, `msg_id`);
