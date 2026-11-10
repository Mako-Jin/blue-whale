-- =====================================================================
-- BlueWhale 微服务平台 - IoT 设备联网平台（whale-iot） Flyway 基线 V1
-- =====================================================================
-- 适用库   : bluewhale_iot（MySQL 8.0+，utf8mb4）
-- Wave     : 6.02（IoT 平台 M0）
-- 说明     : 产品物模型 / 设备 / 遥测 / 指令 / 规则六张核心表。
--            建表规则对齐 whale-assistant/whale-agent V1：IF NOT EXISTS、
--            雪花主键、租户行级隔离、create_time/update_time/deleted 三件套。
--            iot_telemetry 首期 MySQL 按月分区（决策点 #2），量级上来再评估时序库。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 产品表（物模型定义的载体）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_product (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    code             VARCHAR(64)   NOT NULL               COMMENT '产品编码（租户内唯一，如 temp-sensor-v1）',
    name             VARCHAR(128)  NOT NULL               COMMENT '产品名称',
    protocol         VARCHAR(16)   NOT NULL DEFAULT 'mqtt' COMMENT '接入协议：mqtt（首期仅此一种）',
    description      VARCHAR(512)  DEFAULT NULL           COMMENT '产品描述',
    status           VARCHAR(16)   NOT NULL DEFAULT 'draft' COMMENT '状态：draft 开发中 / published 已发布 / disabled 已停用',
    created_by       BIGINT        DEFAULT NULL           COMMENT '创建人用户ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IoT产品表';

-- ---------------------------------------------------------------------
-- 物模型表（属性/事件/服务，JSON Schema 定义）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_thing_model (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    product_id       BIGINT        NOT NULL               COMMENT '所属产品ID（iot_product.id）',
    kind             VARCHAR(16)   NOT NULL               COMMENT '模型类别：property 属性 / event 事件 / service 服务',
    identifier       VARCHAR(64)   NOT NULL               COMMENT '标识符（产品内唯一，如 temperature）',
    name             VARCHAR(128)  NOT NULL               COMMENT '功能名称',
    data_type        VARCHAR(32)   DEFAULT NULL           COMMENT '数据类型（属性：int/double/bool/string/enum/struct）',
    spec_json        MEDIUMTEXT    NULL                   COMMENT '规格JSON（取值范围/步长/枚举项/输入输出参数）',
    required         TINYINT       NOT NULL DEFAULT 0     COMMENT '是否必填：0 否 / 1 是',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_kind_identifier (tenant_id, product_id, kind, identifier),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IoT物模型表';

-- ---------------------------------------------------------------------
-- 设备表（三元组 + 影子状态 + 在线态）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_device (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    product_id       BIGINT        NOT NULL               COMMENT '所属产品ID（iot_product.id）',
    device_name      VARCHAR(64)   NOT NULL               COMMENT '设备名称（产品内唯一；三元组之一）',
    secret           VARCHAR(128)  NOT NULL               COMMENT '设备密钥（三元组之一；签发后仅可重置）',
    nickname         VARCHAR(128)  DEFAULT NULL           COMMENT '设备昵称（展示用）',
    shadow_json      MEDIUMTEXT    NULL                   COMMENT '设备影子JSON（最新上报属性快照 + 期望值）',
    online           TINYINT       NOT NULL DEFAULT 0     COMMENT '在线状态：0 离线 / 1 在线（由连接/断开 Webhook 维护）',
    last_online_time DATETIME      DEFAULT NULL           COMMENT '最近在线时间',
    last_report_time DATETIME      DEFAULT NULL           COMMENT '最近遥测上报时间',
    status           VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT '状态：active 启用 / disabled 禁用',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_device (tenant_id, product_id, device_name),
    KEY idx_tenant_product (tenant_id, product_id, online)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IoT设备表';

-- ---------------------------------------------------------------------
-- 遥测表（按月分区；首期 MySQL，不引入时序库——决策点 #2）
-- 注意：MySQL 分区表要求分区键必须包含在主键/唯一键内，故主键为 (id, ts)。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_telemetry (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法；分区表主键含 ts）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    device_id        BIGINT        NOT NULL               COMMENT '设备ID（iot_device.id）',
    identifier       VARCHAR(64)   NOT NULL               COMMENT '属性标识符（物模型 property）',
    value            DOUBLE        DEFAULT NULL           COMMENT '数值型取值（非数值属性存 value_text）',
    value_text       VARCHAR(512)  DEFAULT NULL           COMMENT '文本型取值（数值属性为 NULL）',
    ts               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '遥测时间戳（分区键）',
    PRIMARY KEY (id, ts),
    KEY idx_device_ts (device_id, identifier, ts),
    KEY idx_tenant_ts (tenant_id, ts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IoT遥测表（按月分区）'
PARTITION BY RANGE (TO_DAYS(ts)) (
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p202701 VALUES LESS THAN (TO_DAYS('2027-02-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- ---------------------------------------------------------------------
-- 指令表（下发/回执/幂等）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_command (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    device_id        BIGINT        NOT NULL               COMMENT '目标设备ID（iot_device.id）',
    identifier       VARCHAR(64)   NOT NULL               COMMENT '服务标识符（物模型 service）',
    params_json      MEDIUMTEXT    NULL                   COMMENT '指令参数JSON（对齐物模型服务入参）',
    message_id       VARCHAR(64)   NOT NULL               COMMENT '消息ID（幂等键；下发方生成或服务端签发）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT '状态：pending 待下发 / sent 已下发 / success 成功 / failed 失败 / timeout 超时',
    reply_json       MEDIUMTEXT    NULL                   COMMENT '设备回执JSON（成功输出或失败原因）',
    issued_by        BIGINT        DEFAULT NULL           COMMENT '下发人用户ID',
    issued_time      DATETIME      DEFAULT NULL           COMMENT '下发时间',
    reply_time       DATETIME      DEFAULT NULL           COMMENT '回执时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_id (message_id),
    KEY idx_device_status (tenant_id, device_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IoT指令表';

-- ---------------------------------------------------------------------
-- 规则表（条件 → 动作；告警动作对接 whale-alert /alert/internal）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iot_rule (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    name             VARCHAR(128)  NOT NULL               COMMENT '规则名称',
    product_id       BIGINT        DEFAULT NULL           COMMENT '限定产品ID（空=全部产品）',
    device_id        BIGINT        DEFAULT NULL           COMMENT '限定设备ID（空=产品下全部设备）',
    condition_json   MEDIUMTEXT    NOT NULL               COMMENT '触发条件JSON（identifier/操作符/阈值/持续时间）',
    action_type      VARCHAR(16)   NOT NULL DEFAULT 'alert' COMMENT '动作类型：alert 告警（whale-alert）/ webhook 回调',
    action_config    MEDIUMTEXT    NULL                   COMMENT '动作配置JSON（告警等级/通知渠道/webhook地址）',
    enabled          TINYINT       NOT NULL DEFAULT 1     COMMENT '是否启用：0 停用 / 1 启用',
    last_fired_time  DATETIME      DEFAULT NULL           COMMENT '最近触发时间（限流去重用）',
    fired_count      BIGINT        NOT NULL DEFAULT 0     COMMENT '累计触发次数',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IoT规则表';
