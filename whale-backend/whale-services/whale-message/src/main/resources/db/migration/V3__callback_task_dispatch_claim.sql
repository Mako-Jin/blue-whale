-- =====================================================================
-- V3: 回调 outbox 多实例抢占（N14）
-- =====================================================================
-- 背景   : V2 的派发器假设单实例，多实例部署时同一任务可能被并发派发。
-- 变更   : msg_callback_task 增加派发实例与抢占时间，配合原子 UPDATE 抢占；
--          抢占超时的任务由派发器回收为 FAILED 重试。
-- 兼容   : 仅新增可空列与索引，历史数据保持 NULL，可安全重复部署。
-- =====================================================================

ALTER TABLE `msg_callback_task`
    ADD COLUMN `dispatch_owner` VARCHAR(64) DEFAULT NULL COMMENT '派发实例标识（抢占成功时写入）' AFTER `status`,
    ADD COLUMN `dispatch_at`    DATETIME    DEFAULT NULL COMMENT '派发抢占时间（超时回收依据）' AFTER `dispatch_owner`,
    ADD KEY `idx_status_dispatch` (`status`, `dispatch_at`);
