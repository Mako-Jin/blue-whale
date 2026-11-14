-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V6：版本快照 append-only
-- =====================================================================
-- 背景   : 版本号表示「发布版本」，同一版本可被多次编辑；原实现按
--          (definition_id, version) 先删后插，导致同一版本的编辑历史被覆盖。
-- 方案   : 新增 snapshot_seq 单调递增序号，每次保存/回滚追加一条；
--          版本号本身保留为发布版本，回滚生成的新版本号由服务层保证不复用。
-- 幂等   : 列/索引均先查 information_schema；历史行用 id 回填 snapshot_seq
--          （雪花 ID 全局唯一，满足同定义内唯一）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 快照序号列
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition_version'
        AND COLUMN_NAME = 'snapshot_seq') = 0,
    'ALTER TABLE form_definition_version ADD COLUMN snapshot_seq BIGINT DEFAULT NULL COMMENT ''快照序号（同定义内单调递增）'' AFTER version',
    'SELECT 1');
PREPARE form_v6_stmt FROM @ddl;
EXECUTE form_v6_stmt;
DEALLOCATE PREPARE form_v6_stmt;

-- ---------------------------------------------------------------------
-- 2. 历史行回填（id 为雪花唯一值，可安全作为初始序号）
-- ---------------------------------------------------------------------
UPDATE form_definition_version SET snapshot_seq = id WHERE snapshot_seq IS NULL;

-- ---------------------------------------------------------------------
-- 3. 快照序号唯一键：(definition_id, snapshot_seq)
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition_version'
        AND INDEX_NAME = 'uk_definition_snapshot_seq') = 0,
    'ALTER TABLE form_definition_version ADD UNIQUE KEY uk_definition_snapshot_seq (definition_id, snapshot_seq)',
    'SELECT 1');
PREPARE form_v6_stmt FROM @ddl;
EXECUTE form_v6_stmt;
DEALLOCATE PREPARE form_v6_stmt;

-- ---------------------------------------------------------------------
-- 4. 移除 V3 的 (definition_id, version) 唯一键：同一版本允许多条编辑快照
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_definition_version'
        AND INDEX_NAME = 'uk_definition_version') > 0,
    'ALTER TABLE form_definition_version DROP INDEX uk_definition_version',
    'SELECT 1');
PREPARE form_v6_stmt FROM @ddl;
EXECUTE form_v6_stmt;
DEALLOCATE PREPARE form_v6_stmt;
