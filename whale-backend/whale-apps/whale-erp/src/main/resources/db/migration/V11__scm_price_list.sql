-- =====================================================================
-- BlueWhale ERP - V11 价格表（M2 剩余 B 第三期：客户售价 / 供应商采购价）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §3.2
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §12.7
-- 规格     : docs/specs/2026-11-05-wave5.78-erp-m2-price-list/spec.md
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 单价 DECIMAL(18,4)；
--            4) 表间逻辑外键，不建物理外键；
--            5) 价格类型 SALES（客户售价）/ PURCHASE（供应商采购价）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 价格表（价格类型 + 往来方 + 物料 租户内唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_price_list (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    price_type        VARCHAR(16)   NOT NULL            COMMENT '价格类型：SALES 客户售价 / PURCHASE 供应商采购价',
    partner_id        BIGINT        NOT NULL            COMMENT '往来方ID（客户或供应商）',
    partner_name      VARCHAR(128)  NOT NULL            COMMENT '往来方名称快照',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    uom               VARCHAR(16)   DEFAULT NULL        COMMENT '计量单位',
    price             DECIMAL(18,4) NOT NULL            COMMENT '单价',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    valid_from        DATE          DEFAULT NULL        COMMENT '有效期起（空表示不限）',
    valid_to          DATE          DEFAULT NULL        COMMENT '有效期止（空表示不限）',
    status            VARCHAR(24)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_price_list (tenant_id, price_type, partner_id, item_id),
    KEY idx_price_list_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='价格表（客户售价+供应商采购价）';
