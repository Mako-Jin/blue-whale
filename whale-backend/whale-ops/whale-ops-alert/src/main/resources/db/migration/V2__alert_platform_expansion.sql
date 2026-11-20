-- =====================================================================
-- V2：告警中心能力扩展（恢复闭环 / 通知可观测 / 静默 / 认领 / 规则接收人）
-- =====================================================================
-- 说明：仅含新增列与新增表，不含 DROP / TRUNCATE / DELETE，可安全增量执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 告警记录：认领字段（配合恢复闭环与"当前活跃告警"统计）
-- ---------------------------------------------------------------------
ALTER TABLE alert_record
    ADD COLUMN acknowledged TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已认领：0 未认领 / 1 已认领',
    ADD COLUMN ack_user     VARCHAR(64)  DEFAULT NULL       COMMENT '认领人ID',
    ADD COLUMN ack_time     DATETIME     DEFAULT NULL       COMMENT '认领时间',
    ADD COLUMN ack_note     VARCHAR(255) DEFAULT NULL       COMMENT '认领备注';

ALTER TABLE alert_record
    ADD KEY idx_tenant_status_trigger (tenant_id, status, trigger_time);

-- ---------------------------------------------------------------------
-- 2. 告警规则：规则级通知接收人（为空时回落到全局配置）
-- ---------------------------------------------------------------------
ALTER TABLE alert_rule
    ADD COLUMN notify_targets VARCHAR(512) DEFAULT NULL COMMENT '规则级接收人，逗号分隔；为空时使用全局配置';

-- ---------------------------------------------------------------------
-- 3. 告警静默表
--    matcher 语义：rule_code / alert_level / keyword 均为空表示"匹配全部"，
--    非空则精确匹配（keyword 为内容包含匹配）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_silence (
    id          BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    rule_code   VARCHAR(64)  DEFAULT NULL            COMMENT '规则编码，空表示全部规则',
    alert_level VARCHAR(16)  DEFAULT NULL            COMMENT '告警级别，空表示全部级别',
    keyword     VARCHAR(128) DEFAULT NULL            COMMENT '内容关键字，空表示全部内容',
    start_time  DATETIME     NOT NULL                COMMENT '静默开始时间',
    end_time    DATETIME     NOT NULL                COMMENT '静默结束时间',
    reason      VARCHAR(255) DEFAULT NULL            COMMENT '静默原因',
    created_by  VARCHAR(64)  DEFAULT NULL            COMMENT '创建人ID',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_window (tenant_id, start_time, end_time)
) COMMENT='告警静默规则表';

-- ---------------------------------------------------------------------
-- 4. 告警通知发送日志表（失败排查与"为什么没收到"）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_notify_log (
    id          BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    record_id   BIGINT       DEFAULT NULL            COMMENT '告警记录ID',
    rule_code   VARCHAR(64)  DEFAULT NULL            COMMENT '规则编码',
    channel     VARCHAR(16)  NOT NULL                COMMENT '通知通道：EMAIL / SMS / WEBHOOK',
    status      VARCHAR(16)  NOT NULL                COMMENT '发送结果：SUCCESS / FAILED',
    retry_count INT          NOT NULL DEFAULT 0      COMMENT '已重试次数',
    error       VARCHAR(512) DEFAULT NULL            COMMENT '失败原因（异常类名 + 消息）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_tenant_rule_time (tenant_id, rule_code, create_time)
) COMMENT='告警通知发送日志表';
