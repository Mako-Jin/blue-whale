-- V13: CRM 分析导出接入 whale-file
-- 导出的 CSV 不再落库暂存，改为上传 whale-file 后保存 file_id / file_name 引用。
-- content 列保留（遗留列，不再写入），避免对已有环境做破坏性 DDL。

SET @ddl := (SELECT IF(
        (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'crm_analytics_export'
           AND column_name = 'file_id') = 0,
    'ALTER TABLE crm_analytics_export ADD COLUMN file_id BIGINT NULL COMMENT ''whale-file 文件ID'' AFTER row_count',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
        (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'crm_analytics_export'
           AND column_name = 'file_name') = 0,
    'ALTER TABLE crm_analytics_export ADD COLUMN file_name VARCHAR(255) NULL COMMENT ''导出文件名（下载用）'' AFTER file_id',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
