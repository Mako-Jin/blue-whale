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
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    remark           VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_rule_code (tenant_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='告警规则表';

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
    trigger_time DATETIME     DEFAULT NULL        COMMENT '触发时间',
    recover_time DATETIME     DEFAULT NULL        COMMENT '恢复时间（未恢复为 NULL）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_rule_code (rule_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='告警记录表';

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
