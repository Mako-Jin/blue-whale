-- =====================================================================
-- BlueWhale 企业资源计划应用（whale-erp）Flyway 增量 V17：CRM 商务订单来源
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M3 / 7f-2）
-- 规则     : 1) 仅追加迁移，不修改历史脚本；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY。
-- 说明     : CRM 审批通过后按 crmOrderNo 幂等下发销售订单；ERP 保留履约订单，
--            source_type / source_id 记录来源，crm_order_no 作为唯一幂等键。
-- =====================================================================

ALTER TABLE erp_scm_sales_order
    ADD COLUMN source_type  VARCHAR(32) DEFAULT NULL COMMENT '来源系统类型：CRM',
    ADD COLUMN source_id    VARCHAR(64) DEFAULT NULL COMMENT '来源单号（CRM 商务订单号）',
    ADD COLUMN crm_order_no VARCHAR(64) DEFAULT NULL COMMENT 'CRM 商务订单号（幂等键）';

ALTER TABLE erp_scm_sales_order
    ADD UNIQUE KEY uk_so_crm_order_no (tenant_id, crm_order_no);
