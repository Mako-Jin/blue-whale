-- ---------------------------------------------------------------------
-- F-03 告警记录指纹去重（2026-10-27）
-- 幂等：列/索引不存在时才新增，可重复执行。
-- ---------------------------------------------------------------------
SET @has_fingerprint := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alert_record' AND COLUMN_NAME = 'fingerprint'
);
SET @add_fingerprint := IF(@has_fingerprint = 0,
    'ALTER TABLE alert_record ADD COLUMN fingerprint VARCHAR(64) DEFAULT NULL COMMENT ''告警指纹（Alertmanager fingerprint 或标签 SHA-256），用于窗口内去重'' AFTER status',
    'SELECT 1');
PREPARE stmt_fingerprint FROM @add_fingerprint;
EXECUTE stmt_fingerprint;
DEALLOCATE PREPARE stmt_fingerprint;

SET @has_fingerprint_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alert_record' AND INDEX_NAME = 'idx_fingerprint'
);
SET @add_fingerprint_index := IF(@has_fingerprint_index = 0,
    'ALTER TABLE alert_record ADD INDEX idx_fingerprint (tenant_id, fingerprint, trigger_time)',
    'SELECT 1');
PREPARE stmt_fingerprint_index FROM @add_fingerprint_index;
EXECUTE stmt_fingerprint_index;
DEALLOCATE PREPARE stmt_fingerprint_index;
