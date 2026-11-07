-- =====================================================================
-- BlueWhale 企业资源计划应用（whale-erp）Flyway 增量 V18：CRM 销售退货来源
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M4 / 7f-4c）
-- 规则     : 1) 仅追加迁移，不修改历史脚本；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY。
-- 说明     : CRM 退换货审批通过后按 crmReturnNo 幂等下发销售退货并过账；
--            crm_return_no 作为唯一幂等键，source_type / source_id 记录来源。
-- =====================================================================

ALTER TABLE erp_scm_sales_return
    ADD COLUMN crm_return_no VARCHAR(64) DEFAULT NULL COMMENT 'CRM 退换货申请单号（幂等键）',
    ADD COLUMN source_type   VARCHAR(32) DEFAULT NULL COMMENT '来源系统类型：CRM',
    ADD COLUMN source_id     VARCHAR(64) DEFAULT NULL COMMENT '来源单号（CRM 退换货申请单号）';

ALTER TABLE erp_scm_sales_return
    ADD UNIQUE KEY uk_sr_crm_return_no (tenant_id, crm_return_no);
