-- V10: 发件箱/采集事件幂等键扩宽
-- 背景：进度回传幂等键 `mes:work-order-progress:{workOrderId}:{reported}:{qualified}:{completed}:{status}`
-- 在工单状态为 IN_PROGRESS 时实际长度可达 65 字符，超出 source_event_id VARCHAR(64) 上限，
-- 导致报工（/mes/execution/report）写入发件箱时 Data too long 而整体失败（2026-11-09 实机联调发现）。
-- 幂等键语义保持不变，扩宽承载列；同时放宽设备状态事件与指标采样的同名列，避免同类键溢出。
ALTER TABLE mes_common_outbox
    MODIFY COLUMN source_event_id VARCHAR(128) NOT NULL COMMENT '源事件幂等键';
ALTER TABLE mes_eq_status_event
    MODIFY COLUMN source_event_id VARCHAR(128) NULL COMMENT '源事件幂等键';
ALTER TABLE mes_cl_metric_sample
    MODIFY COLUMN source_event_id VARCHAR(128) NULL COMMENT '源事件幂等键';
