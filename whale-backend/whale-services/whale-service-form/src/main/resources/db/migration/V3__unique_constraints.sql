-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V3：发布登记唯一约束
-- =====================================================================
-- 背景   : form_table_registry 与 form_definition_version 此前仅有普通索引，
--          唯一性依赖服务层「先查后插」，并发发布/回滚时可产生重复登记或重复快照。
-- 幂等   : 每个索引先查 information_schema，已存在则执行 SELECT 1 跳过，可重复执行。
-- 范围   : 仅对「只追加、不做逻辑删除」的登记类表加唯一约束；
--          form_definition / form_table_definition 含逻辑删除，唯一索引会与
--          「软删后按同编码重建」冲突，故维持普通索引 + 服务层前置校验。
-- 注意   : 若存量库已存在重复登记，本脚本会失败，需先人工清理重复行再执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 元数据模型：（租户, 表编码, 表版本）唯一
-- 旧模型行此两列为 NULL，MySQL 唯一索引允许多个 NULL，不影响存量
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_table_registry'
        AND INDEX_NAME = 'uk_tenant_table_code_version') = 0,
    'ALTER TABLE form_table_registry ADD UNIQUE KEY uk_tenant_table_code_version (tenant_id, table_code, table_version)',
    'SELECT 1');
PREPARE form_unique_stmt FROM @ddl;
EXECUTE form_unique_stmt;
DEALLOCATE PREPARE form_unique_stmt;

-- ---------------------------------------------------------------------
-- 旧模型：（租户, 表单编码, 表单版本）唯一
-- 元数据模型行此两列为 NULL，同样不受影响
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_table_registry'
        AND INDEX_NAME = 'uk_tenant_definition_code_version') = 0,
    'ALTER TABLE form_table_registry ADD UNIQUE KEY uk_tenant_definition_code_version (tenant_id, definition_code, definition_version)',
    'SELECT 1');
PREPARE form_unique_stmt FROM @ddl;
EXECUTE form_unique_stmt;
DEALLOCATE PREPARE form_unique_stmt;

-- ---------------------------------------------------------------------
-- 版本快照：（表单定义, 版本）唯一（写快照前先删同版本旧行，兼容唯一约束）
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition_version'
        AND INDEX_NAME = 'uk_definition_version') = 0,
    'ALTER TABLE form_definition_version ADD UNIQUE KEY uk_definition_version (definition_id, version)',
    'SELECT 1');
PREPARE form_unique_stmt FROM @ddl;
EXECUTE form_unique_stmt;
DEALLOCATE PREPARE form_unique_stmt;
