-- =====================================================================
-- BlueWhale 微服务平台 - 告警中心服务（whale-alert）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_alert
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成，无自增）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除，与 MyBatis-Plus
--              logic-delete-field: deleted 配置对齐）
-- 命名风格   : 列名统一下划线风格，与 mybatis-plus map-underscore-to-camel-case 对齐
-- 说明       : 表间关联均为逻辑外键（不建物理 FOREIGN KEY），便于微服务独立演进
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 告警规则表
--    rule_code 业务唯一：因逻辑删除保留行，不建唯一索引，唯一性由服务层前置校验保证。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS alert_rule;
CREATE TABLE alert_rule (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    rule_name        VARCHAR(128)  NOT NULL                COMMENT '规则名称',
    rule_code        VARCHAR(64)   NOT NULL                COMMENT '规则编码（租户内逻辑唯一），如 cpu_high',
    metric           VARCHAR(64)   DEFAULT NULL            COMMENT '监控指标，如 cpu.usage',
    target           VARCHAR(128)  DEFAULT NULL            COMMENT '监控目标（服务名 / 实例 / 资源标识）',
    operator         VARCHAR(16)   DEFAULT NULL            COMMENT '比较运算符：GT / GE / LT / LE / EQ / NE',
    threshold        DECIMAL(18,4) DEFAULT NULL            COMMENT '触发阈值',
    duration_seconds INT           DEFAULT NULL            COMMENT '持续时长（秒），持续超过阈值达到该时长才触发',
    level            VARCHAR(16)   DEFAULT NULL            COMMENT '告警级别：INFO / WARN / ERROR / CRITICAL',
    channel          VARCHAR(32)   DEFAULT NULL            COMMENT '通知通道：EMAIL / SMS / WEBHOOK / NONE',
    notify_targets   VARCHAR(512)  DEFAULT NULL            COMMENT '规则级接收人，逗号分隔；为空时使用全局配置',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    remark           VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_rule_code (tenant_id, rule_code)
) COMMENT='告警规则表';

-- ---------------------------------------------------------------------
-- 2. 告警记录表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS alert_record;
CREATE TABLE alert_record (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    rule_id      BIGINT       DEFAULT NULL        COMMENT '告警规则ID（逻辑关联 alert_rule.id）',
    rule_code    VARCHAR(64)  DEFAULT NULL        COMMENT '告警规则编码（冗余，便于记录独立查询）',
    alert_level  VARCHAR(16)  DEFAULT NULL        COMMENT '告警级别：INFO / WARN / ERROR / CRITICAL',
    content      VARCHAR(512) DEFAULT NULL        COMMENT '告警内容描述',
    status       VARCHAR(16)  DEFAULT NULL        COMMENT '记录状态：FIRING 触发中 / RECOVERED 已恢复',
    fingerprint  VARCHAR(64)  DEFAULT NULL        COMMENT '告警指纹（Alertmanager fingerprint 或标签 SHA-256），用于窗口内去重',
    trigger_time DATETIME     DEFAULT NULL        COMMENT '触发时间',
    recover_time DATETIME     DEFAULT NULL        COMMENT '恢复时间（未恢复为 NULL）',
    acknowledged TINYINT      NOT NULL DEFAULT 0  COMMENT '是否已认领：0 未认领 / 1 已认领',
    ack_user     VARCHAR(64)  DEFAULT NULL        COMMENT '认领人ID',
    ack_time     DATETIME     DEFAULT NULL        COMMENT '认领时间',
    ack_note     VARCHAR(255) DEFAULT NULL        COMMENT '认领备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_rule_code (rule_code),
    KEY idx_status (status),
    KEY idx_tenant_status_trigger (tenant_id, status, trigger_time),
    KEY idx_fingerprint (tenant_id, fingerprint, trigger_time)
) COMMENT='告警记录表';

-- ---------------------------------------------------------------------
-- 种子规则（便于接口冒烟与前端联调）
-- ---------------------------------------------------------------------
INSERT INTO alert_rule (id, rule_name, rule_code, metric, target, operator, threshold,
                        duration_seconds, level, channel, status, remark)
VALUES
    (1, 'CPU 使用率过高', 'cpu_high', 'cpu.usage', 'whale-gateway', 'GT', 80.0000,
     60, 'WARN', 'EMAIL', 1, '内置种子'),
    (2, '实例宕机', 'instance_down', 'instance.healthy', '*', 'EQ', 0.0000,
     30, 'CRITICAL', 'NONE', 1, '内置种子');

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS api_log;
CREATE TABLE api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    query_string   VARCHAR(1024) DEFAULT NULL           COMMENT '查询串（敏感字段已脱敏）',
    request_params TEXT          NULL                   COMMENT '请求体（JSON，敏感字段已脱敏）',
    response_body  TEXT          NULL                   COMMENT '响应体（JSON，敏感字段已脱敏，超限截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码（HTTP 状态）',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '调用方IP（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_user_id (user_id),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_biz_status (biz_status)
) COMMENT='后端接口日志表';

-- ---------------------------------------------------------------------
-- 告警静默规则表（V2 新增）
-- matcher 语义：rule_code / alert_level / keyword 均为空表示匹配全部，
-- 非空则精确匹配（keyword 为内容包含匹配）。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS alert_silence;
CREATE TABLE alert_silence (
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
-- 告警通知发送日志表（V2 新增）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS alert_notify_log;
CREATE TABLE alert_notify_log (
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