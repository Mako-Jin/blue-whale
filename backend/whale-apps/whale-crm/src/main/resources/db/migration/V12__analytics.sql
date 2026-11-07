-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V12：M6 数据分析与行动计划
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md（§3.6 / §4.2 / §5.1）
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M6）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 不写业务种子数据。
-- 说明     : 指标快照按「租户 + 指标 + 统计日 + 维度类型 + 维度值」唯一，
--            重跑只更新不新增；行动项按 dedup_key 幂等去重；导出内容为阶段性
--            临时存储，后续接入 whale-file 后改为 fileId 引用。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 指标快照（M6；唯一键 uk_crm_metric_snapshot）
--    dimension_type: TOTAL / OWNER / DEPT / SOURCE / INDUSTRY
--    TOTAL 维度 dimension_id 固定为 '0'
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_metric_snapshot (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    metric_code           VARCHAR(64)   NOT NULL                COMMENT '指标编码（见 MetricCode）',
    stat_date             DATE          NOT NULL                COMMENT '统计日期',
    dimension_type        VARCHAR(16)   NOT NULL DEFAULT 'TOTAL' COMMENT '维度类型：TOTAL / OWNER / DEPT / SOURCE / INDUSTRY',
    dimension_id          VARCHAR(64)   NOT NULL DEFAULT '0'    COMMENT '维度值（TOTAL 为 0，其余为负责人/部门/来源/行业编码）',
    metric_value          DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '指标值（金额/数量/百分比/评分，按指标口径）',
    metric_extra          VARCHAR(500)  DEFAULT NULL            COMMENT '扩展信息（JSON，来源与计算说明）',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_metric_snapshot (tenant_id, metric_code, stat_date, dimension_type, dimension_id),
    KEY idx_crm_metric_snapshot_date (tenant_id, stat_date)
) COMMENT='CRM 指标快照（按唯一键幂等覆盖）';

-- ---------------------------------------------------------------------
-- 2. 行动计划规则（M6；规则编码租户内唯一）
--    rule_type: CUSTOMER_IDLE / OPPORTUNITY_OVERDUE / TARGET_GAP /
--               CONTRACT_EXPIRE / AR_OVERDUE / CSAT_LOW
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_action_rule (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    rule_code             VARCHAR(64)   NOT NULL                COMMENT '规则编码（租户内唯一）',
    rule_name             VARCHAR(128)  NOT NULL                COMMENT '规则名称',
    rule_type             VARCHAR(24)   NOT NULL                COMMENT '规则类型：CUSTOMER_IDLE / OPPORTUNITY_OVERDUE / TARGET_GAP / CONTRACT_EXPIRE / AR_OVERDUE / CSAT_LOW',
    threshold_days        INT           NOT NULL DEFAULT 0      COMMENT '阈值天数（按规则类型解释）',
    enabled               TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 停用',
    description           VARCHAR(500)  DEFAULT NULL            COMMENT '规则说明',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_action_rule_code (tenant_id, rule_code),
    KEY idx_crm_action_rule_type (tenant_id, rule_type, enabled)
) COMMENT='CRM 行动计划规则';

-- ---------------------------------------------------------------------
-- 3. 行动计划项（M6；按 dedup_key 幂等，条件解除后自动关闭）
--    ref_type: CUSTOMER / OPPORTUNITY / TARGET / CONTRACT / INVOICE / TICKET
--    priority: P1 / P2 / P3
--    status  : OPEN / CLOSED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_action_item (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    rule_code             VARCHAR(64)   NOT NULL                COMMENT '来源规则编码',
    ref_type              VARCHAR(16)   NOT NULL                COMMENT '关联对象类型：CUSTOMER / OPPORTUNITY / TARGET / CONTRACT / INVOICE / TICKET',
    ref_id                BIGINT        NOT NULL                COMMENT '关联对象ID',
    customer_id           BIGINT        DEFAULT NULL            COMMENT '客户ID快照（可空）',
    customer_name         VARCHAR(255)  DEFAULT NULL            COMMENT '客户名称快照',
    title                 VARCHAR(255)  NOT NULL                COMMENT '行动项标题',
    suggestion            VARCHAR(500)  DEFAULT NULL            COMMENT '行动建议',
    priority              VARCHAR(8)    NOT NULL DEFAULT 'P2'   COMMENT '优先级：P1 / P2 / P3',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    due_date              DATE          DEFAULT NULL            COMMENT '建议完成日期',
    status                VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN / CLOSED',
    close_reason          VARCHAR(500)  DEFAULT NULL            COMMENT '关闭原因（手工关闭必填）',
    closed_by             BIGINT        DEFAULT NULL            COMMENT '关闭人',
    closed_at             DATETIME      DEFAULT NULL            COMMENT '关闭时间',
    dedup_key             VARCHAR(128)  NOT NULL                COMMENT '去重键（同租户唯一）',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_action_item_dedup (tenant_id, dedup_key),
    KEY idx_crm_action_item_scan (tenant_id, status, priority, due_date),
    KEY idx_crm_action_item_owner (tenant_id, owner_id, status),
    KEY idx_crm_action_item_rule (tenant_id, rule_code)
) COMMENT='CRM 行动计划项';

-- ---------------------------------------------------------------------
-- 4. 分析导出任务（M6；单号租户内唯一）
--    export_type: CUSTOMER / OPPORTUNITY / ORDER / COLLECTION / ACTION
--    status     : RUNNING / COMPLETED / FAILED
--    content    : 阶段性临时方案，CSV 文本直接落库，单条导出上限受
--                 whale.crm.analytics.export-max-rows 约束；接入 whale-file
--                 后改为 fileId 引用并清理本列历史数据。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_analytics_export (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    export_no             VARCHAR(64)   NOT NULL                COMMENT '导出单号（EXP-yyyyMMdd-xxxx）',
    export_type           VARCHAR(16)   NOT NULL                COMMENT '导出类型：CUSTOMER / OPPORTUNITY / ORDER / COLLECTION / ACTION',
    filters_json          TEXT          NULL                    COMMENT '筛选条件（JSON）',
    status                VARCHAR(16)   NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING / COMPLETED / FAILED',
    row_count             INT           NOT NULL DEFAULT 0      COMMENT '导出数据行数',
    content               MEDIUMTEXT    NULL                    COMMENT 'CSV 内容（临时方案，接入 whale-file 后改为 fileId）',
    error                 VARCHAR(500)  DEFAULT NULL            COMMENT '失败原因',
    requested_by          BIGINT        DEFAULT NULL            COMMENT '发起人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_export_no (tenant_id, export_no),
    KEY idx_crm_export_query (tenant_id, export_type, status)
) COMMENT='CRM 分析导出任务（content 为阶段性临时存储）';
