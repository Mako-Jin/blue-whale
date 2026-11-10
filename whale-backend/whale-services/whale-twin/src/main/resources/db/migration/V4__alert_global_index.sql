-- ---------------------------------------------------------------------
-- Wave 6.08 M1：跨场景告警总览——全局未恢复/级别/确认状态查询索引
-- ---------------------------------------------------------------------
ALTER TABLE twin_alert
    ADD INDEX idx_alert_resolved_level (resolved, level),
    ADD INDEX idx_alert_resolved_ack (resolved, ack);
