-- =====================================================================
-- BlueWhale ERP - V8 数据分析与报表中心（M6：驾驶舱 / 多维 / 报表 / 预警）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §3.6 / §5.2 / §6.2
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §M6
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / CREATE OR REPLACE VIEW / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，数量 DECIMAL(18,4)，比率 DECIMAL(9,4)；
--            4) analytics 不拥有交易事实，只读消费事实视图（禁止跨上下文写表）；
--            5) 预警事件按「规则 + 对象 + 周期」唯一，保证同规则同对象同周期不重复触发。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 经营驾驶舱快照（按期间 + 统计日缓存，刷新时旧快照置 STALE）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_bi_cockpit_snapshot (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    snapshot_no       VARCHAR(64)   NOT NULL            COMMENT '快照编号（租户内唯一）',
    period_code       CHAR(6)       NOT NULL            COMMENT '统计期间 yyyyMM',
    stat_date         DATE          NOT NULL            COMMENT '统计截止日期',
    revenue_amount    DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '收入金额',
    cost_amount       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '成本金额',
    profit_amount     DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '利润金额',
    order_count       INT           NOT NULL DEFAULT 0  COMMENT '订单数',
    alert_count       INT           NOT NULL DEFAULT 0  COMMENT '未关闭预警数',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 有效 / STALE 已失效',
    refreshed_at      DATETIME      NOT NULL            COMMENT '刷新时间',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_cockpit_snapshot_no (tenant_id, snapshot_no),
    UNIQUE KEY uk_bi_cockpit_period_date (tenant_id, period_code, stat_date),
    KEY idx_bi_cockpit_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='经营驾驶舱快照';

-- ---------------------------------------------------------------------
-- 2. 报表定义（数据源 + 维度 + 指标 + 过滤条件）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_bi_report_def (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    report_no         VARCHAR(64)   NOT NULL            COMMENT '报表编号（租户内唯一）',
    report_name       VARCHAR(128)  NOT NULL            COMMENT '报表名称',
    report_type       VARCHAR(16)   NOT NULL DEFAULT 'CUSTOM' COMMENT '类型：SALES/STOCK/RECEIVABLE/CUSTOM',
    dimension_type    VARCHAR(16)   NOT NULL DEFAULT 'CUSTOMER' COMMENT '维度：CUSTOMER/ITEM/WAREHOUSE',
    metric_type       VARCHAR(16)   NOT NULL DEFAULT 'REVENUE' COMMENT '指标：REVENUE/QUANTITY/ORDER_COUNT/COST',
    filter_json       VARCHAR(1024) DEFAULT NULL        COMMENT '过滤条件 JSON（日期区间等）',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED / DISABLED',
    owner             VARCHAR(64)   DEFAULT NULL        COMMENT '创建人',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_report_no (tenant_id, report_no),
    KEY idx_bi_report_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表定义';

-- ---------------------------------------------------------------------
-- 3. 报表定时计划（定时触发；外部推送暂缓，仅记录计划状态）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_bi_report_schedule (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    schedule_no       VARCHAR(64)   NOT NULL            COMMENT '计划编号（租户内唯一）',
    report_no         VARCHAR(64)   NOT NULL            COMMENT '报表编号',
    schedule_name     VARCHAR(128)  NOT NULL            COMMENT '计划名称',
    cron_expression   VARCHAR(64)   NOT NULL            COMMENT 'Cron 表达式',
    channel           VARCHAR(16)   NOT NULL DEFAULT 'INTERNAL' COMMENT '推送渠道：INTERNAL/EMAIL/MESSAGE',
    recipients        VARCHAR(512)  DEFAULT NULL        COMMENT '接收人（逗号分隔）',
    last_run_time     DATETIME      DEFAULT NULL        COMMENT '上次执行时间',
    next_run_time     DATETIME      DEFAULT NULL        COMMENT '下次执行时间',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED / DISABLED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_schedule_no (tenant_id, schedule_no),
    KEY idx_bi_schedule_report (tenant_id, report_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表定时计划';

-- ---------------------------------------------------------------------
-- 4. 预警规则（库存不足 / 超期应收 / 质量异常 / 超预算）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_bi_alert_rule (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    rule_no           VARCHAR(64)   NOT NULL            COMMENT '规则编号（租户内唯一）',
    rule_name         VARCHAR(128)  NOT NULL            COMMENT '规则名称',
    rule_type         VARCHAR(24)   NOT NULL            COMMENT '类型：STOCK_LOW/RECEIVABLE_OVERDUE/QUALITY_OPEN_NCR/BUDGET_EXCEEDED',
    threshold_value   DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '阈值（库存数量 / 超期天数 / 超预算比例）',
    period_type       VARCHAR(16)   NOT NULL DEFAULT 'DAY' COMMENT '统计周期：DAY/WEEK/MONTH',
    level             VARCHAR(16)   NOT NULL DEFAULT 'WARN' COMMENT '级别：INFO/WARN/CRITICAL',
    suggestion        VARCHAR(512)  DEFAULT NULL        COMMENT '计划建议模板',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED / DISABLED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_alert_rule_no (tenant_id, rule_no),
    KEY idx_bi_alert_rule_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预警规则';

-- ---------------------------------------------------------------------
-- 5. 预警事件（同规则 + 同对象 + 同周期唯一，重复触发只更新不新增）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_bi_alert_event (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    event_no          VARCHAR(64)   NOT NULL            COMMENT '事件编号（租户内唯一）',
    rule_id           BIGINT        NOT NULL            COMMENT '规则ID',
    rule_no           VARCHAR(64)   NOT NULL            COMMENT '规则编号快照',
    rule_type         VARCHAR(24)   NOT NULL            COMMENT '规则类型快照',
    object_key        VARCHAR(128)  NOT NULL            COMMENT '预警对象键（物料+仓库 / 应收单号 / 不合格品单号）',
    period_code       VARCHAR(16)   NOT NULL            COMMENT '统计周期编码：yyyyMMdd / yyyyWW / yyyyMM',
    metric_value      DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '实际值',
    threshold_value   DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '阈值',
    level             VARCHAR(16)   NOT NULL DEFAULT 'WARN' COMMENT '级别：INFO/WARN/CRITICAL',
    status            VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/ACKNOWLEDGED/CLOSED',
    suggestion        VARCHAR(512)  DEFAULT NULL        COMMENT '计划建议',
    triggered_at      DATETIME      NOT NULL            COMMENT '触发时间',
    acknowledged_by   VARCHAR(64)   DEFAULT NULL        COMMENT '确认人',
    acknowledged_at   DATETIME      DEFAULT NULL        COMMENT '确认时间',
    closed_by         VARCHAR(64)   DEFAULT NULL        COMMENT '关闭人',
    closed_at         DATETIME      DEFAULT NULL        COMMENT '关闭时间',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bi_alert_event_no (tenant_id, event_no),
    UNIQUE KEY uk_bi_alert_event_scope (tenant_id, rule_id, object_key, period_code),
    KEY idx_bi_alert_event_status (tenant_id, status),
    KEY idx_bi_alert_event_type (tenant_id, rule_type, triggered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预警事件';

-- ---------------------------------------------------------------------
-- 6. 只读事实视图（analytics 只读投影，不复制交易事实）
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_erp_bi_sales_fact AS
SELECT so.tenant_id        AS tenant_id,
       so.doc_no           AS doc_no,
       so.doc_date         AS doc_date,
       so.customer_id      AS customer_id,
       so.customer_name    AS customer_name,
       sol.item_id         AS item_id,
       sol.item_code       AS item_code,
       sol.item_name       AS item_name,
       so.warehouse_id     AS warehouse_id,
       sol.quantity        AS quantity,
       sol.shipped_quantity AS shipped_quantity,
       sol.amount          AS amount
FROM erp_scm_sales_order so
JOIN erp_scm_sales_order_line sol
  ON sol.tenant_id = so.tenant_id
 AND sol.order_id = so.id
 AND sol.deleted = 0
WHERE so.deleted = 0
  AND so.status IN ('APPROVED', 'PARTIALLY_SHIPPED', 'SHIPPED', 'CLOSED');

CREATE OR REPLACE VIEW v_erp_bi_cost_fact AS
SELECT m.tenant_id    AS tenant_id,
       m.item_id      AS item_id,
       m.item_code    AS item_code,
       m.item_name    AS item_name,
       m.warehouse_id AS warehouse_id,
       CAST(m.occurred_at AS DATE) AS occurred_date,
       m.quantity     AS quantity,
       m.amount       AS amount
FROM erp_scm_stock_movement m
WHERE m.deleted = 0
  AND m.direction = 'OUT';

CREATE OR REPLACE VIEW v_erp_bi_stock_fact AS
SELECT b.tenant_id    AS tenant_id,
       b.item_id      AS item_id,
       b.item_code    AS item_code,
       b.item_name    AS item_name,
       b.warehouse_id AS warehouse_id,
       b.quantity     AS quantity,
       b.amount       AS amount
FROM erp_scm_stock_balance b
WHERE b.deleted = 0;

CREATE OR REPLACE VIEW v_erp_bi_receivable_fact AS
SELECT r.tenant_id       AS tenant_id,
       r.doc_no          AS doc_no,
       r.customer_id     AS customer_id,
       r.customer_name   AS customer_name,
       r.doc_date        AS doc_date,
       r.due_date        AS due_date,
       r.amount          AS amount,
       r.settled_amount  AS settled_amount,
       r.bad_debt_amount AS bad_debt_amount,
       r.status          AS status
FROM erp_fin_receivable r
WHERE r.deleted = 0;

CREATE OR REPLACE VIEW v_erp_bi_quality_fact AS
SELECT n.tenant_id   AS tenant_id,
       n.ncr_no      AS ncr_no,
       n.source_no   AS source_no,
       n.item_id     AS item_id,
       n.item_code   AS item_code,
       n.item_name   AS item_name,
       n.quantity    AS quantity,
       n.status      AS status,
       n.cost_amount AS cost_amount,
       n.create_time AS created_at
FROM erp_qc_ncr n
WHERE n.deleted = 0;

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'erp_bi_%';
--   SELECT * FROM v_erp_bi_sales_fact LIMIT 10;
-- =====================================================================
