-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V10：表单数据审批状态
-- =====================================================================
-- 背景   : FormData.status 仅有 1 有效 / 0 作废，缺少「草稿/审批中/已通过/已驳回」。
-- 决策   : 审批流由外部工作流（whale-workflow）承载，本模块只保存业务状态与流程实例ID，
--          并据此实现「审批中只读」；状态推进由工作流回调驱动，不在本模块建状态机。
-- 口径   : biz_status 1 草稿 / 2 审批中 / 3 已通过 / 4 已驳回。
--          存量数据与未接入审批的新数据默认 3（已通过），保证升级后行为不变。
-- 幂等   : 列与索引均按 information_schema 判定后补建。
-- =====================================================================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data' AND COLUMN_NAME = 'biz_status') = 0,
    'ALTER TABLE form_data ADD COLUMN biz_status TINYINT NOT NULL DEFAULT 3 COMMENT ''业务状态：1 草稿 / 2 审批中 / 3 已通过 / 4 已驳回''',
    'SELECT 1');
PREPARE form_v10_stmt FROM @ddl;
EXECUTE form_v10_stmt;
DEALLOCATE PREPARE form_v10_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data' AND COLUMN_NAME = 'process_instance_id') = 0,
    'ALTER TABLE form_data ADD COLUMN process_instance_id VARCHAR(64) DEFAULT NULL COMMENT ''外部工作流实例ID''',
    'SELECT 1');
PREPARE form_v10_stmt FROM @ddl;
EXECUTE form_v10_stmt;
DEALLOCATE PREPARE form_v10_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data'
        AND INDEX_NAME = 'idx_process_instance') = 0,
    'ALTER TABLE form_data ADD KEY idx_process_instance (process_instance_id)',
    'SELECT 1');
PREPARE form_v10_stmt FROM @ddl;
EXECUTE form_v10_stmt;
DEALLOCATE PREPARE form_v10_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data'
        AND INDEX_NAME = 'idx_tenant_biz_status') = 0,
    'ALTER TABLE form_data ADD KEY idx_tenant_biz_status (tenant_id, biz_status)',
    'SELECT 1');
PREPARE form_v10_stmt FROM @ddl;
EXECUTE form_v10_stmt;
DEALLOCATE PREPARE form_v10_stmt;
