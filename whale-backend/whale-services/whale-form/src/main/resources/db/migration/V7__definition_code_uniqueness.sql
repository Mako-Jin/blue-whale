-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V7：编码活跃唯一键
-- =====================================================================
-- 背景   : assertCodeUnique / assertTableCodeUnique 是 check-then-act，
--          并发创建仍可产生同编码定义；而直接给 (tenant_id, code) 加唯一键
--          会与逻辑删除冲突（软删后无法用同编码重建）。
-- 方案   : 生成列 active_code / active_table_code = IF(deleted = 0, code, NULL)，
--          唯一键包含生成列；MySQL 唯一索引允许多个 NULL，软删行互不冲突。
-- 幂等   : 列与索引均先查 information_schema，可重复执行。
-- 注意   : 若存量数据已有同租户重复编码，本迁移会失败并暴露脏数据，需先人工清理。
-- =====================================================================

-- ---------------------------------------------------------------------
-- form_definition：活跃编码唯一
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition'
        AND COLUMN_NAME = 'active_code') = 0,
    'ALTER TABLE form_definition ADD COLUMN active_code VARCHAR(64) GENERATED ALWAYS AS (IF(deleted = 0, code, NULL)) STORED COMMENT ''活跃编码（逻辑删除后为 NULL）''',
    'SELECT 1');
PREPARE form_v7_stmt FROM @ddl;
EXECUTE form_v7_stmt;
DEALLOCATE PREPARE form_v7_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition'
        AND INDEX_NAME = 'uk_tenant_active_code') = 0,
    'ALTER TABLE form_definition ADD UNIQUE KEY uk_tenant_active_code (tenant_id, active_code)',
    'SELECT 1');
PREPARE form_v7_stmt FROM @ddl;
EXECUTE form_v7_stmt;
DEALLOCATE PREPARE form_v7_stmt;

-- ---------------------------------------------------------------------
-- form_table_definition：活跃表编码唯一
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_table_definition'
        AND COLUMN_NAME = 'active_table_code') = 0,
    'ALTER TABLE form_table_definition ADD COLUMN active_table_code VARCHAR(64) GENERATED ALWAYS AS (IF(deleted = 0, table_code, NULL)) STORED COMMENT ''活跃表编码（逻辑删除后为 NULL）''',
    'SELECT 1');
PREPARE form_v7_stmt FROM @ddl;
EXECUTE form_v7_stmt;
DEALLOCATE PREPARE form_v7_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_table_definition'
        AND INDEX_NAME = 'uk_tenant_active_table_code') = 0,
    'ALTER TABLE form_table_definition ADD UNIQUE KEY uk_tenant_active_table_code (tenant_id, active_table_code)',
    'SELECT 1');
PREPARE form_v7_stmt FROM @ddl;
EXECUTE form_v7_stmt;
DEALLOCATE PREPARE form_v7_stmt;
