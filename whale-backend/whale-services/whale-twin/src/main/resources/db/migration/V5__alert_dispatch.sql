-- ---------------------------------------------------------------------
-- Wave 6.10 M1：告警外发投递记录（webhook / 站内信每次投递的成败留痕）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS twin_alert_dispatch (
    id           BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    alert_id     BIGINT       DEFAULT NULL           COMMENT '告警ID（twin_alert.id；恢复类事件为空）',
    scene_id     BIGINT       NOT NULL               COMMENT '所属场景ID（twin_scene.id）',
    state_key    VARCHAR(64)  NOT NULL               COMMENT '实体状态键',
    level        VARCHAR(16)  NOT NULL               COMMENT '触发级别：warn / critical',
    channel      VARCHAR(16)  NOT NULL               COMMENT '通道：WEBHOOK / INAPP',
    target       VARCHAR(256) DEFAULT NULL           COMMENT '投递目标（webhook 地址或接收人概要）',
    status       VARCHAR(16)  NOT NULL               COMMENT '结果：SUCCESS / FAILED / SKIPPED',
    message_id   BIGINT       DEFAULT NULL           COMMENT '站内信消息ID（whale-message 返回）',
    error_msg    VARCHAR(512) DEFAULT NULL           COMMENT '失败原因（截断 512）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_dispatch_alert (alert_id),
    KEY idx_dispatch_scene_time (scene_id, create_time),
    KEY idx_dispatch_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='孪生告警外发投递记录表';
