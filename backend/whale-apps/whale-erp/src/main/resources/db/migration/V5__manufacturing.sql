-- =====================================================================
-- BlueWhale ERP - V5 生产计划（M3 第一阶段：预测 / 主生产计划 / 生产订单）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §5.1
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §M3
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，数量/单位成本 DECIMAL(18,4)；
--            4) 生产领料/完工入库复用 erp_scm_stock_movement / erp_scm_stock_balance；
--            5) 表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 需求预测批次（period + version 标识版本，DRAFT → PUBLISHED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_forecast (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    batch_no          VARCHAR(64)   NOT NULL            COMMENT '预测批次号（租户内唯一）',
    period            VARCHAR(16)   NOT NULL            COMMENT '预测期间（YYYY-MM）',
    version           INT           NOT NULL DEFAULT 1  COMMENT '版本号',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/CANCELLED',
    total_quantity    DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '预测总数量',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forecast_batch (tenant_id, batch_no),
    KEY idx_forecast_period (tenant_id, period, version),
    KEY idx_forecast_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='需求预测批次';

-- ---------------------------------------------------------------------
-- 2. 需求预测分录（物料 + 期间 + 版本逻辑唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_forecast_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    forecast_id       BIGINT        NOT NULL            COMMENT '预测批次ID',
    line_no           INT           NOT NULL            COMMENT '行号（批次内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '预测数量',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_forecast_line (tenant_id, forecast_id, line_no),
    KEY idx_forecast_line_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='需求预测分录';

-- ---------------------------------------------------------------------
-- 3. 主生产计划（DRAFT → RELEASED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_mps (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    plan_no           VARCHAR(64)   NOT NULL            COMMENT '计划单号（租户内唯一）',
    period            VARCHAR(16)   NOT NULL            COMMENT '计划期间（YYYY-MM）',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/RELEASED/CANCELLED',
    total_planned     DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '计划总数量',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mps_plan_no (tenant_id, plan_no),
    KEY idx_mps_period (tenant_id, period),
    KEY idx_mps_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='主生产计划';

-- ---------------------------------------------------------------------
-- 4. 主生产计划分录（下达时 released_quantity = planned_quantity）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_mps_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    mps_id            BIGINT        NOT NULL            COMMENT '主生产计划ID',
    line_no           INT           NOT NULL            COMMENT '行号（计划内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    planned_quantity  DECIMAL(18,4) NOT NULL            COMMENT '计划数量',
    released_quantity DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '已下达数量',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mps_line (tenant_id, mps_id, line_no),
    KEY idx_mps_line_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='主生产计划分录';

-- ---------------------------------------------------------------------
-- 5. 生产订单头（DRAFT → RELEASED → IN_PROGRESS → COMPLETED → CLOSED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_production_order (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_no          VARCHAR(64)   NOT NULL            COMMENT '生产订单号（租户内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '产出物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '产出物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '产出物料名称快照',
    planned_quantity  DECIMAL(18,4) NOT NULL            COMMENT '计划生产数量',
    completed_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已完工数量',
    completed_cost    DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '已入库成本',
    total_material_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '累计领料成本',
    issue_warehouse_id BIGINT       NOT NULL            COMMENT '领料仓库ID',
    issue_warehouse_name VARCHAR(128) NOT NULL          COMMENT '领料仓库名称快照',
    target_warehouse_id BIGINT      NOT NULL            COMMENT '完工入库仓库ID',
    target_warehouse_name VARCHAR(128) NOT NULL         COMMENT '完工入库仓库名称快照',
    plan_start_date   DATE          NOT NULL            COMMENT '计划开工日期',
    plan_end_date     DATE          DEFAULT NULL        COMMENT '计划完工日期',
    complete_date     DATE          DEFAULT NULL        COMMENT '实际完工日期',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/RELEASED/IN_PROGRESS/COMPLETED/CLOSED/CANCELLED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mo_order_no (tenant_id, order_no),
    KEY idx_mo_date (plan_start_date),
    KEY idx_mo_status (status),
    KEY idx_mo_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产订单头';

-- ---------------------------------------------------------------------
-- 6. 生产用料（领料数量 ≤ 需求数量）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_production_material (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id          BIGINT        NOT NULL            COMMENT '生产订单ID',
    line_no           INT           NOT NULL            COMMENT '行号（订单内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '用料物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '用料物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '用料物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    required_quantity DECIMAL(18,4) NOT NULL            COMMENT '需求数量',
    issued_quantity   DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '已领数量',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mo_material (tenant_id, order_id, line_no),
    KEY idx_mo_material_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产订单用料';

-- ---------------------------------------------------------------------
-- 7. 生产成本科目种子（D5 科目映射确认后只需改 erp.finance.account.production-cost）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO erp_account (id, tenant_id, code, name, account_type, direction, leaf_flag, status, remark)
VALUES (7022, 1, '5001', '生产成本', 'EXPENSE', 'DEBIT', 1, 'ENABLED', '内置科目');
