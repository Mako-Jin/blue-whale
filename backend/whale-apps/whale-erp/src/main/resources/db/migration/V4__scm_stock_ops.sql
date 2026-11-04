-- =====================================================================
-- BlueWhale ERP - V4 库存作业（M2 剩余 A：调拨 / 盘点）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §5.1
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §M2
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / ALTER TABLE / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，数量/单价/单位成本 DECIMAL(18,4)；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 库存调拨单头（DRAFT → POSTED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_transfer (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '调拨单号（租户内唯一）',
    transfer_date     DATE          NOT NULL            COMMENT '调拨日期',
    from_warehouse_id BIGINT        NOT NULL            COMMENT '调出仓库ID',
    from_warehouse_name VARCHAR(128) NOT NULL           COMMENT '调出仓库名称快照',
    to_warehouse_id   BIGINT        NOT NULL            COMMENT '调入仓库ID',
    to_warehouse_name VARCHAR(128)  NOT NULL            COMMENT '调入仓库名称快照',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/POSTED/CANCELLED',
    total_quantity    DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '调拨总数量',
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '调拨总金额（成本口径）',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_doc_no (tenant_id, doc_no),
    KEY idx_transfer_from (tenant_id, from_warehouse_id),
    KEY idx_transfer_to (tenant_id, to_warehouse_id),
    KEY idx_transfer_date (transfer_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存调拨单头';

-- ---------------------------------------------------------------------
-- 2. 库存调拨分录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_transfer_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    transfer_id       BIGINT        NOT NULL            COMMENT '调拨单ID',
    line_no           INT           NOT NULL            COMMENT '行号（单内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '调拨数量',
    unit_cost         DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '单位成本（调出仓移动加权）',
    amount            DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '调拨金额',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_line (tenant_id, transfer_id, line_no),
    KEY idx_transfer_line_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存调拨分录';

-- ---------------------------------------------------------------------
-- 3. 库存盘点单头（DRAFT → POSTED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_stocktake (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '盘点单号（租户内唯一）',
    stocktake_date    DATE          NOT NULL            COMMENT '盘点日期',
    warehouse_id      BIGINT        NOT NULL            COMMENT '盘点仓库ID',
    warehouse_name    VARCHAR(128)  NOT NULL            COMMENT '仓库名称快照',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/POSTED/CANCELLED',
    gain_amount       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '盘盈金额',
    loss_amount       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '盘亏金额',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stocktake_doc_no (tenant_id, doc_no),
    KEY idx_stocktake_warehouse (tenant_id, warehouse_id),
    KEY idx_stocktake_date (stocktake_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存盘点单头';

-- ---------------------------------------------------------------------
-- 4. 库存盘点分录（账面数量建档时快照，差异 = 实盘 - 账面）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_stocktake_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    stocktake_id      BIGINT        NOT NULL            COMMENT '盘点单ID',
    line_no           INT           NOT NULL            COMMENT '行号（单内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    book_quantity     DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '账面数量（建档时快照）',
    counted_quantity  DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '实盘数量',
    difference        DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '差异数量（正=盘盈，负=盘亏）',
    unit_cost         DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '单位成本（账面移动加权）',
    amount            DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '差异金额（正=盘盈，负=盘亏）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stocktake_line (tenant_id, stocktake_id, line_no),
    KEY idx_stocktake_line_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存盘点分录';

-- ---------------------------------------------------------------------
-- 5. 待处理财产损溢科目（盘盈/盘亏凭证使用，D5 确认前使用内置科目）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO erp_account (id, tenant_id, code, name, account_type, direction, leaf_flag, status, remark)
VALUES (7021, 1, '1901', '待处理财产损溢', 'ASSET', 'DEBIT', 1, 'ENABLED', '内置科目');
