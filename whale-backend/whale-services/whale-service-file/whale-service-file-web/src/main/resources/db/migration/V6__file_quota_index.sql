-- =====================================================================
-- whale-file 批次 7：租户/桶级配额聚合查询索引
-- =====================================================================
-- 背景：桶维度配额按 (tenant_id, bucket) 聚合正常文件字节数，此前只有 tenant_id
--       单列索引，桶维度聚合退化为租户内全表扫描。
-- 幂等：先查 information_schema.STATISTICS，索引已存在时跳过，重复执行不报错。
-- =====================================================================
SET @has_quota_index = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'idx_tenant_bucket');
SET @ddl = IF(@has_quota_index = 0,
    'ALTER TABLE file_info ADD INDEX idx_tenant_bucket (tenant_id, bucket)',
    'SELECT ''file_info.idx_tenant_bucket 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
