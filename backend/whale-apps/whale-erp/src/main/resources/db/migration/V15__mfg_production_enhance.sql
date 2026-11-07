-- =====================================================================
-- BlueWhale ERP - V15 生产增强（工序报工 / 倒冲领料 / 退料与报废）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §5.x
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §13.7
-- 规则     : 1) ALTER TABLE ADD COLUMN / CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE；
--            3) 数量 DECIMAL(18,4)，金额 DECIMAL(18,2)；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 生产用料：倒冲标记与已领成本（退料/报废按已领成本冲减）
-- ---------------------------------------------------------------------
ALTER TABLE erp_mfg_production_material
    ADD COLUMN backflush TINYINT NOT NULL DEFAULT 0 COMMENT '倒冲领料：0 否 / 1 是',
    ADD COLUMN issued_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '累计已领成本';

-- ---------------------------------------------------------------------
-- 2. 生产用料调整记录（退料 RETURN / 报废 SCRAP）；报废不回库存
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_production_material_adj (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id            BIGINT        NOT NULL            COMMENT '生产订单ID',
    doc_no              VARCHAR(64)   NOT NULL            COMMENT '调整单号（租户内唯一）',
    adj_type            VARCHAR(16)   NOT NULL            COMMENT '调整类型：RETURN/SCRAP',
    line_no             INT           NOT NULL            COMMENT '生产用料行号',
    item_id             BIGINT        NOT NULL            COMMENT '物料ID',
    item_code           VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name           VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    quantity            DECIMAL(18,4) NOT NULL            COMMENT '调整数量',
    amount              DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '调整金额（按已领成本冲减）',
    reason              VARCHAR(255)  DEFAULT NULL        COMMENT '原因',
    operator_id         BIGINT        DEFAULT NULL        COMMENT '操作人ID',
    occurred_at         DATETIME      NOT NULL            COMMENT '发生时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mfg_material_adj_doc (tenant_id, doc_no),
    KEY idx_mfg_material_adj_order (tenant_id, order_id),
    KEY idx_mfg_material_adj_type (tenant_id, adj_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产用料调整记录';
