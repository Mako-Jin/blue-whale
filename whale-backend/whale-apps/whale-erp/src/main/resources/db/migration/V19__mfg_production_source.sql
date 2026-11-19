-- =====================================================================
-- BlueWhale 企业资源计划应用（whale-erp）Flyway 增量 V19：生产订单来源单据
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 计划     : docs/plans/2026-11-19-m1-manufacturing-chain-closure.md（M-A）
-- 规则     : 1) 仅追加迁移，不修改历史脚本；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY。
-- 说明     : M1 制造链要求「销售订单 → 生产订单」可追溯。人工建单的订单来源字段为 NULL；
--            由销售订单行转单生成的订单记录来源类型、订单主键、单号与行号，
--            并以唯一键保证同一销售订单行只能转一张生产订单（MySQL 唯一索引允许多个 NULL）。
-- =====================================================================

ALTER TABLE erp_mfg_production_order
    ADD COLUMN source_type     VARCHAR(32) DEFAULT NULL COMMENT '来源单据类型：SALES_ORDER',
    ADD COLUMN source_order_id BIGINT DEFAULT NULL COMMENT '来源单据主键',
    ADD COLUMN source_order_no VARCHAR(64) DEFAULT NULL COMMENT '来源单号',
    ADD COLUMN source_line_no  INT DEFAULT NULL COMMENT '来源单据行号';

ALTER TABLE erp_mfg_production_order
    ADD UNIQUE KEY uk_mfg_prod_source (source_type, source_order_id, source_line_no);
