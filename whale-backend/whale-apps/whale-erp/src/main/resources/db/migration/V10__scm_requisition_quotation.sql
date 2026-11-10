-- =====================================================================
-- BlueWhale ERP - V10 供应链申请与报价（M2 剩余 B 第二期：采购申请 / 销售报价）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §5.1
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §12.4
-- 规格     : docs/specs/2026-11-05-wave5.78-erp-m2-requisition-quotation/spec.md
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / ALTER TABLE / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，数量/单价 DECIMAL(18,4)；
--            4) 表间逻辑外键，不建物理外键；
--            5) 申请单 DRAFT → APPROVED → CONVERTED；报价单 DRAFT → ACCEPTED → CONVERTED。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 采购申请单头（DRAFT → APPROVED → CONVERTED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_purchase_requisition (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '申请单号（租户内唯一）',
    supplier_id       BIGINT        NOT NULL            COMMENT '供应商ID',
    supplier_name     VARCHAR(128)  NOT NULL            COMMENT '供应商名称快照',
    doc_date          DATE          NOT NULL            COMMENT '申请日期',
    required_date     DATE          DEFAULT NULL        COMMENT '需求到货日期',
    warehouse_id      BIGINT        NOT NULL            COMMENT '收货仓库ID',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/APPROVED/CONVERTED/CANCELLED',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '申请总额',
    converted_order_no VARCHAR(64)  DEFAULT NULL        COMMENT '转单生成的采购订单号',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pa_doc_no (tenant_id, doc_no),
    KEY idx_pa_supplier (tenant_id, supplier_id),
    KEY idx_pa_date (doc_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购申请单头';

-- ---------------------------------------------------------------------
-- 2. 采购申请分录（单价为预估单价）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_purchase_requisition_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    requisition_id    BIGINT        NOT NULL            COMMENT '申请单ID',
    line_no           INT           NOT NULL            COMMENT '行号（单内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '申请数量',
    unit_price        DECIMAL(18,4) NOT NULL            COMMENT '预估单价',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '行金额',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pa_line (tenant_id, requisition_id, line_no),
    KEY idx_pa_line_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采购申请分录';

-- ---------------------------------------------------------------------
-- 3. 销售报价单头（DRAFT → ACCEPTED → CONVERTED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_sales_quotation (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no            VARCHAR(64)   NOT NULL            COMMENT '报价单号（租户内唯一）',
    customer_id       BIGINT        NOT NULL            COMMENT '客户ID',
    customer_name     VARCHAR(128)  NOT NULL            COMMENT '客户名称快照',
    doc_date          DATE          NOT NULL            COMMENT '报价日期',
    valid_until       DATE          DEFAULT NULL        COMMENT '报价有效期至',
    warehouse_id      BIGINT        NOT NULL            COMMENT '发货仓库ID',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ACCEPTED/CONVERTED/CANCELLED',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    total_amount      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '报价总额',
    converted_order_no VARCHAR(64)  DEFAULT NULL        COMMENT '转单生成的销售订单号',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sq_doc_no (tenant_id, doc_no),
    KEY idx_sq_customer (tenant_id, customer_id),
    KEY idx_sq_date (doc_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售报价单头';

-- ---------------------------------------------------------------------
-- 4. 销售报价分录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_sales_quotation_line (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    quotation_id      BIGINT        NOT NULL            COMMENT '报价单ID',
    line_no           INT           NOT NULL            COMMENT '行号（单内唯一）',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '报价数量',
    unit_price        DECIMAL(18,4) NOT NULL            COMMENT '报价单价',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '行金额',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sq_line (tenant_id, quotation_id, line_no),
    KEY idx_sq_line_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='销售报价分录';
