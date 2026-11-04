-- =====================================================================
-- BlueWhale ERP - V9 供应链退货（M2 剩余 B 第一期：采购退货 / 销售退货）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §5.1
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §12.4
-- 规格     : docs/specs/2026-11-04-wave5.78-erp-m2-returns/spec.md
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / ALTER TABLE / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，数量/单价/单位成本 DECIMAL(18,4)；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY；
--            5) 退货单据 DRAFT → POSTED / CANCELLED，过账后不可撤销。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 采购退货单头（DRAFT → POSTED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_purchase_return (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '退货单号（租户内唯一）',
    purchase_order_id BIGINT        NOT NULL            COMMENT '源采购订单ID',
    supplier_id       BIGINT        NOT NULL            COMMENT '供应商ID',
    supplier_name     VARCHAR(128)  NOT NULL            COMMENT '供应商名称快照',
    doc_date          DATE          NOT NULL            COMMENT '退货日期',
    warehouse_id      BIGINT        NOT NULL            COMMENT '退货出库仓库ID',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/POSTED/CANCELLED',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '退货总额',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pr_doc_no (tenant_id, doc_no),
    KEY idx_pr_order (tenant_id, purchase_order_id),
    KEY idx_pr_supplier (tenant_id, supplier_id),
    KEY idx_pr_date (doc_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购退货单头';

-- ---------------------------------------------------------------------
-- 2. 采购退货分录（数量 ≤ 订单行已收数量 − 已退货数量）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_purchase_return_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    return_id         BIGINT        NOT NULL            COMMENT '退货单ID',
    line_no           INT           NOT NULL            COMMENT '行号（单内唯一）',
    order_line_no     INT           NOT NULL            COMMENT '源订单行号',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '退货数量',
    unit_price        DECIMAL(18,2) NOT NULL            COMMENT '订单单价',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '行金额',
    unit_cost         DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '出库单位成本（=订单单价）',
    cost_amount       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '出库成本金额',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pr_line (tenant_id, return_id, line_no),
    KEY idx_pr_line_item (tenant_id, item_id),
    KEY idx_pr_line_order (tenant_id, order_line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购退货分录';

-- ---------------------------------------------------------------------
-- 3. 销售退货单头（DRAFT → POSTED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_sales_return (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '退货单号（租户内唯一）',
    sales_order_id    BIGINT        NOT NULL            COMMENT '源销售订单ID',
    customer_id       BIGINT        NOT NULL            COMMENT '客户ID',
    customer_name     VARCHAR(128)  NOT NULL            COMMENT '客户名称快照',
    doc_date          DATE          NOT NULL            COMMENT '退货日期',
    warehouse_id      BIGINT        NOT NULL            COMMENT '退货入库仓库ID',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/POSTED/CANCELLED',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '退货总额',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sr_doc_no (tenant_id, doc_no),
    KEY idx_sr_order (tenant_id, sales_order_id),
    KEY idx_sr_customer (tenant_id, customer_id),
    KEY idx_sr_date (doc_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售退货单头';

-- ---------------------------------------------------------------------
-- 4. 销售退货分录（数量 ≤ 订单行已发数量 − 已退货数量；成本过账时回写）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_sales_return_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    return_id         BIGINT        NOT NULL            COMMENT '退货单ID',
    line_no           INT           NOT NULL            COMMENT '行号（单内唯一）',
    order_line_no     INT           NOT NULL            COMMENT '源订单行号',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '退货数量',
    unit_price        DECIMAL(18,2) NOT NULL            COMMENT '订单单价',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '行金额',
    unit_cost         DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '入库单位成本（当前移动加权）',
    cost_amount       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '入库成本金额',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sr_line (tenant_id, return_id, line_no),
    KEY idx_sr_line_item (tenant_id, item_id),
    KEY idx_sr_line_order (tenant_id, order_line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售退货分录';
