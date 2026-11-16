-- =====================================================================
-- whale-file 批次 29：版本组内版本号唯一约束
-- =====================================================================
-- 背景：uploadVersion / rollback 以「组内最大版本号 + 1」计算新版本号，并发上传可能算出
--       相同版本号并写入重复版本行。由数据库唯一索引兜底，应用层捕获冲突后重算重试。
-- 口径：parent_id 为版本组根文件ID（根版本为 NULL）；MySQL 唯一索引允许多个 NULL，
--       因此根版本不受约束，仅约束同一版本组内的版本号。
-- 范围：包含逻辑删除行——已删除版本占用的序号不再复用，与「最大版本号查询含已删除行」一致。
-- 幂等：唯一索引已存在时跳过；新建唯一索引后移除被其完全覆盖的普通索引 idx_parent_version。
-- 上线前查重（有结果表示存量已有重复版本号，需人工处理后再执行迁移）：
--   SELECT parent_id, version, COUNT(*) FROM file_info
--   WHERE parent_id IS NOT NULL GROUP BY parent_id, version HAVING COUNT(*) > 1;
-- =====================================================================
SET @has_unique_version = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'uk_parent_version');
SET @has_plain_version = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'idx_parent_version');
SET @ddl = IF(@has_unique_version = 0,
    IF(@has_plain_version > 0,
        'ALTER TABLE file_info ADD UNIQUE INDEX uk_parent_version (parent_id, version), DROP INDEX idx_parent_version',
        'ALTER TABLE file_info ADD UNIQUE INDEX uk_parent_version (parent_id, version)'),
    'SELECT ''file_info.uk_parent_version 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
