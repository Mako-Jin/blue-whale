-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V11：表单分类与标签
-- =====================================================================
-- 背景   : 表单模板需要按「分类」分组、按「标签」检索（评审扩展项第 5 项），
--          当前 form_definition 只有 code/name/status，无法表达分组与标签。
-- 方案   : 新增 category（单值分类，便于等值过滤与索引）与 tags（JSON 数组文本，
--          如 ["报销","财务"]），并给 category 建普通索引支撑分页过滤。
-- 幂等   : 列与索引均先查 information_schema 判定，已存在时执行 SELECT 1 跳过。
-- =====================================================================

-- ---------------------------------------------------------------------
-- form_definition：表单分类
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition'
        AND COLUMN_NAME = 'category') = 0,
    'ALTER TABLE form_definition ADD COLUMN category VARCHAR(64) DEFAULT NULL COMMENT ''表单分类''',
    'SELECT 1');
PREPARE form_v11_stmt FROM @ddl;
EXECUTE form_v11_stmt;
DEALLOCATE PREPARE form_v11_stmt;

-- ---------------------------------------------------------------------
-- form_definition：表单标签（JSON 字符串数组文本）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition'
        AND COLUMN_NAME = 'tags') = 0,
    'ALTER TABLE form_definition ADD COLUMN tags VARCHAR(512) DEFAULT NULL COMMENT ''标签JSON数组，如 ["报销","财务"]''',
    'SELECT 1');
PREPARE form_v11_stmt FROM @ddl;
EXECUTE form_v11_stmt;
DEALLOCATE PREPARE form_v11_stmt;

-- ---------------------------------------------------------------------
-- form_definition：分类过滤索引
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition'
        AND INDEX_NAME = 'idx_def_category') = 0,
    'ALTER TABLE form_definition ADD KEY idx_def_category (category)',
    'SELECT 1');
PREPARE form_v11_stmt FROM @ddl;
EXECUTE form_v11_stmt;
DEALLOCATE PREPARE form_v11_stmt;
