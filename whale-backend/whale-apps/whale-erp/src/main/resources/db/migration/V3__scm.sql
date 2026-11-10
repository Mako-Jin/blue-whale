-- =====================================================================
-- BlueWhale ERP - V3 供应链核心（M2：采购 / 销售 / 库存）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §5.1
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §M2
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / ALTER TABLE / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，数量/单价/单位成本 DECIMAL(18,4)；
--            4) 库存流水只追加，余额由流水汇总；
--            5) 表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 采购订单头（DRAFT → APPROVED → PARTIALLY_RECEIVED → RECEIVED → CLOSED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_purchase_order (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '订单号（租户内唯一）',
    supplier_id       BIGINT        NOT NULL            COMMENT '供应商ID',
    supplier_name     VARCHAR(128)  NOT NULL            COMMENT '供应商名称快照',
    doc_date          DATE          NOT NULL            COMMENT '单据日期',
    expected_date     DATE          DEFAULT NULL        COMMENT '预计到货日期',
    warehouse_id      BIGINT        NOT NULL            COMMENT '收货仓库ID',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/APPROVED/PARTIALLY_RECEIVED/RECEIVED/CLOSED/CANCELLED',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '订单总额',
    received_amount   DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '已收货金额',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_doc_no (tenant_id, doc_no),
    KEY idx_po_supplier (tenant_id, supplier_id),
    KEY idx_po_date (doc_date),
    KEY idx_po_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购订单头';

-- ---------------------------------------------------------------------
-- 2. 采购订单分录（已收数量 ≤ 订单数量）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_purchase_order_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id          BIGINT        NOT NULL            COMMENT '订单ID',
    line_no           INT           NOT NULL            COMMENT '行号（订单内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '订单数量',
    received_quantity DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '已收数量',
    unit_price        DECIMAL(18,2) NOT NULL            COMMENT '单价',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '行金额',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_line (tenant_id, order_id, line_no),
    KEY idx_po_line_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购订单分录';

-- ---------------------------------------------------------------------
-- 3. 销售订单头（DRAFT → APPROVED → PARTIALLY_SHIPPED → SHIPPED → CLOSED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_sales_order (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '订单号（租户内唯一）',
    customer_id       BIGINT        NOT NULL            COMMENT '客户ID',
    customer_name     VARCHAR(128)  NOT NULL            COMMENT '客户名称快照',
    doc_date          DATE          NOT NULL            COMMENT '单据日期',
    delivery_date     DATE          DEFAULT NULL        COMMENT '计划发货日期',
    warehouse_id      BIGINT        NOT NULL            COMMENT '发货仓库ID',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/APPROVED/PARTIALLY_SHIPPED/SHIPPED/CLOSED/CANCELLED',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '订单总额',
    shipped_amount    DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '已发货金额',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_so_doc_no (tenant_id, doc_no),
    KEY idx_so_customer (tenant_id, customer_id),
    KEY idx_so_date (doc_date),
    KEY idx_so_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售订单头';

-- ---------------------------------------------------------------------
-- 4. 销售订单分录（已发数量 ≤ 订单数量）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_sales_order_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id          BIGINT        NOT NULL            COMMENT '订单ID',
    line_no           INT           NOT NULL            COMMENT '行号（订单内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '订单数量',
    shipped_quantity  DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '已发数量',
    unit_price        DECIMAL(18,2) NOT NULL            COMMENT '单价',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '行金额',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_so_line (tenant_id, order_id, line_no),
    KEY idx_so_line_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售订单分录';

-- ---------------------------------------------------------------------
-- 5. 库存移动流水（只追加：收货 / 发货 / 盘点调整）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_stock_movement (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '关联单据号（收货单/发货单/调整单）',
    source_type       VARCHAR(32)   NOT NULL            COMMENT '来源类型：GOODS_RECEIPT/SHIPMENT/STOCK_ADJUST',
    source_id         VARCHAR(64)   NOT NULL            COMMENT '来源单据号（幂等键）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    warehouse_id      BIGINT        NOT NULL            COMMENT '仓库ID',
    direction         VARCHAR(8)    NOT NULL            COMMENT '方向：IN 入库 / OUT 出库',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '数量',
    unit_cost         DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '单位成本',
    amount            DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '金额',
    occurred_at       DATETIME      NOT NULL            COMMENT '发生时间',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_stock_move_item (tenant_id, item_id, warehouse_id),
    KEY idx_stock_move_doc (tenant_id, doc_no),
    KEY idx_stock_move_source (source_type, source_id),
    KEY idx_stock_move_time (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存移动流水（只追加）';

-- ---------------------------------------------------------------------
-- 6. 库存余额（物料 + 仓库唯一，移动加权平均计价）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_stock_balance (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    warehouse_id      BIGINT        NOT NULL            COMMENT '仓库ID',
    quantity          DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '结存数量',
    amount            DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '结存金额',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_balance (tenant_id, item_id, warehouse_id),
    KEY idx_stock_balance_warehouse (tenant_id, warehouse_id),
    KEY idx_stock_balance_item_code (tenant_id, item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存余额（移动加权平均）';

-- ---------------------------------------------------------------------
-- 7. 自动记账幂等键：来源单据（租户 + 来源类型 + 来源单号）唯一
--    MySQL 唯一索引允许多个 NULL，手工单据不受影响
-- ---------------------------------------------------------------------
ALTER TABLE erp_fin_voucher
    ADD UNIQUE KEY uk_voucher_source (tenant_id, source_type, source_id);

ALTER TABLE erp_fin_receivable
    ADD UNIQUE KEY uk_receivable_source (tenant_id, source_type, source_id);

ALTER TABLE erp_fin_payable
    ADD UNIQUE KEY uk_payable_source (tenant_id, source_type, source_id);
