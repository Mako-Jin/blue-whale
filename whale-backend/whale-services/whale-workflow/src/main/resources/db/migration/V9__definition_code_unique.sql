-- =====================================================================
-- BlueWhale - whale-workflow Flyway 增量 V9：流程编码活跃唯一约束
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 说明     : service 层「先 count 再 insert」在并发下可写入重复编码。
--            MySQL 无法对逻辑删除行建部分唯一索引，这里增加生成列
--            active_code（未删除时为 code，已删除时为 NULL），并对
--            (tenant_id, active_code) 建唯一键，由数据库兜底并发。
-- 幂等     : 使用 information_schema 守卫，重复执行安全。
-- =====================================================================

-- 1) 存量去重：同租户同编码仅保留最早一条有效记录，其余置为已删除，
--    避免新增唯一键时因历史脏数据失败
UPDATE process_definition p
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY tenant_id, code ORDER BY id) AS rn
        FROM process_definition
        WHERE deleted = 0
    ) ranked
    WHERE ranked.rn > 1
) dup ON p.id = dup.id
SET p.deleted = 1,
    p.update_time = NOW();

-- 2) 新增活跃编码生成列
SET @has_active_code := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_definition'
      AND COLUMN_NAME = 'active_code'
);
SET @ddl := IF(@has_active_code = 0,
    'ALTER TABLE process_definition ADD COLUMN active_code VARCHAR(64) GENERATED ALWAYS AS (IF(deleted = 0, code, NULL)) STORED COMMENT ''活跃编码（逻辑删除后为 NULL，用于唯一约束）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 建立租户内活跃编码唯一键
SET @has_uk_active_code := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_definition'
      AND INDEX_NAME = 'uk_tenant_active_code'
);
SET @ddl := IF(@has_uk_active_code = 0,
    'ALTER TABLE process_definition ADD UNIQUE KEY uk_tenant_active_code (tenant_id, active_code)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
