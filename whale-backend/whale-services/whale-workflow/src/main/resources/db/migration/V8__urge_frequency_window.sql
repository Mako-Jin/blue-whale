-- =====================================================================
-- BlueWhale - whale-workflow Flyway 增量 V8：催办频控时间窗唯一键
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 说明     : 「查 count → 判断 → insert」在并发下可绕过 1 小时频控。
--            这里增加 window_bucket（创建时间截断到小时）并建立唯一键，
--            由数据库唯一约束兜底并发重复催办。
-- 幂等     : MySQL 不支持 ADD COLUMN/ADD INDEX IF NOT EXISTS，
--            统一使用 information_schema 守卫，保证重复执行安全。
-- =====================================================================

-- 1) 补齐频控时间窗列
SET @has_window_bucket := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_urge'
      AND COLUMN_NAME = 'window_bucket'
);
SET @ddl := IF(@has_window_bucket = 0,
    'ALTER TABLE process_urge ADD COLUMN window_bucket VARCHAR(16) DEFAULT NULL COMMENT ''频控时间窗（创建时间截断到小时 yyyyMMddHH）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 存量回填：按创建时间截断到小时
UPDATE process_urge
SET window_bucket = DATE_FORMAT(create_time, '%Y%m%d%H')
WHERE window_bucket IS NULL;

-- 3) 存量去重：同一 (租户, 实例, 催办人, 时间窗) 仅保留最早一条参与唯一键，
--    其余置空（MySQL 唯一键允许多个 NULL），避免建唯一键时因历史重复数据失败
UPDATE process_urge p
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, instance_id, urger, window_bucket
                   ORDER BY id
               ) AS rn
        FROM process_urge
        WHERE window_bucket IS NOT NULL
    ) ranked
    WHERE ranked.rn > 1
) dup ON p.id = dup.id
SET p.window_bucket = NULL;

-- 4) 建立频控唯一键
SET @has_uk_urge_window := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_urge'
      AND INDEX_NAME = 'uk_urge_window'
);
SET @ddl := IF(@has_uk_urge_window = 0,
    'ALTER TABLE process_urge ADD UNIQUE KEY uk_urge_window (tenant_id, instance_id, urger, window_bucket)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
