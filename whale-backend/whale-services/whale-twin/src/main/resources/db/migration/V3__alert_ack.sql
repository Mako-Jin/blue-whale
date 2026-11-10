-- ---------------------------------------------------------------------
-- Wave 6.07 M1：告警确认（ACK）——人工知悉越界事件，与"恢复"语义相互独立
-- ---------------------------------------------------------------------
ALTER TABLE twin_alert
    ADD COLUMN ack       TINYINT  NOT NULL DEFAULT 0   COMMENT '是否已确认：0 未确认 / 1 已确认',
    ADD COLUMN ack_time  DATETIME DEFAULT NULL         COMMENT '确认时间',
    ADD COLUMN ack_by    BIGINT   DEFAULT NULL         COMMENT '确认人用户ID',
    ADD INDEX idx_scene_ack (scene_id, ack);
