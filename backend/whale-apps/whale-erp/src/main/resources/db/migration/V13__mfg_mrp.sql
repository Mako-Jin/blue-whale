-- =====================================================================
-- BlueWhale ERP - V13 MRP（M3 剩余一期：BOM 展开 / 净需求 / 计划订单 / 追溯）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §5.3
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §13.5
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE / ALTER（基线测试强校验）；
--            3) 数量 DECIMAL(18,4)；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY；
--            5) BOM 快照来自 PDM 内网契约，ERP 只保存运行输入与结果。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. MRP 运行记录（RUNNING → COMPLETED / FAILED，可重跑生成新记录）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_mrp_run (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    run_no              VARCHAR(64)   NOT NULL            COMMENT '运行单号（租户内唯一）',
    status              VARCHAR(24)   NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/COMPLETED/FAILED',
    demand_line_count   INT           NOT NULL DEFAULT 0  COMMENT '需求输入行数',
    snapshot_line_count INT           NOT NULL DEFAULT 0  COMMENT 'BOM 快照总行数',
    planned_order_count INT           NOT NULL DEFAULT 0  COMMENT '生成计划订单数',
    error_message       VARCHAR(500)  DEFAULT NULL        COMMENT '失败原因（FAILED 时必填）',
    remark              VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    start_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time            DATETIME      DEFAULT NULL        COMMENT '结束时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mrp_run_no (tenant_id, run_no),
    KEY idx_mrp_run_status (tenant_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP运行记录';

-- ---------------------------------------------------------------------
-- 2. MRP 输入需求行（持久化用于重跑与结果复现）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_mrp_run_line (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    run_id              BIGINT        NOT NULL            COMMENT '运行记录ID',
    line_no             INT           NOT NULL            COMMENT '行号（运行内唯一）',
    item_code           VARCHAR(64)   NOT NULL            COMMENT '需求物料编码',
    bom_no              VARCHAR(64)   NOT NULL            COMMENT 'PDM BOM 编号',
    bom_version         VARCHAR(32)   NOT NULL            COMMENT 'PDM BOM 版本',
    quantity            DECIMAL(18,4) NOT NULL            COMMENT '需求数量',
    due_date            DATE          NOT NULL            COMMENT '需求日期',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mrp_run_line (tenant_id, run_id, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP输入需求行';

-- ---------------------------------------------------------------------
-- 3. MRP 计划订单（按物料聚合，PURCHASE 采购建议 / PRODUCTION 生产建议）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_mrp_planned_order (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    run_id              BIGINT        NOT NULL            COMMENT '运行记录ID',
    order_no            VARCHAR(64)   NOT NULL            COMMENT '计划订单号（租户内唯一）',
    order_type          VARCHAR(24)   NOT NULL            COMMENT '建议类型：PURCHASE/PRODUCTION',
    item_id             BIGINT        NOT NULL            COMMENT '物料ID',
    item_code           VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name           VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom                 VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    gross_quantity      DECIMAL(18,4) NOT NULL            COMMENT '毛需求量',
    available_quantity  DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '现货可用量（跨仓库汇总）',
    net_quantity        DECIMAL(18,4) NOT NULL            COMMENT '净需求量（毛需求 - 可用量，且不为负）',
    item_level          INT           NOT NULL DEFAULT 1  COMMENT 'BOM 最小层级',
    bom_no              VARCHAR(64)   DEFAULT NULL        COMMENT '来源 BOM 编号',
    bom_version         VARCHAR(32)   DEFAULT NULL        COMMENT '来源 BOM 版本',
    need_date           DATE          DEFAULT NULL        COMMENT '需求日期（取来源行最早日期）',
    status              VARCHAR(24)   NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mrp_order_no (tenant_id, order_no),
    KEY idx_mrp_order_run (tenant_id, run_id),
    KEY idx_mrp_order_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP计划订单';

-- ---------------------------------------------------------------------
-- 4. MRP 需求追溯（计划订单 → 来源需求行 + BOM path）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_mrp_trace (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    run_id              BIGINT        NOT NULL            COMMENT '运行记录ID',
    planned_order_id    BIGINT        NOT NULL            COMMENT '计划订单ID',
    demand_line_no      INT           NOT NULL            COMMENT '来源需求行号',
    bom_no              VARCHAR(64)   NOT NULL            COMMENT 'BOM 编号',
    bom_version         VARCHAR(32)   NOT NULL            COMMENT 'BOM 版本',
    bom_path            VARCHAR(255)  NOT NULL            COMMENT 'BOM 路径（深度优先，如 1 / 1.1）',
    depth               INT           NOT NULL            COMMENT 'BOM 层级',
    unit_qty            DECIMAL(18,4) NOT NULL            COMMENT '单位累计用量（含损耗）',
    loss_rate           DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '损耗率（0-100 百分比，保留快照值）',
    gross_quantity      DECIMAL(18,4) NOT NULL            COMMENT '贡献毛需求量 = 需求数量 × 单位累计用量',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mrp_trace_order (tenant_id, run_id, planned_order_id),
    KEY idx_mrp_trace_line (tenant_id, run_id, demand_line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MRP需求追溯';
