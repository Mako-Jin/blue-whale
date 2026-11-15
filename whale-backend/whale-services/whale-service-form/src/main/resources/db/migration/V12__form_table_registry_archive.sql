-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V12：物理表登记冷热分离
-- =====================================================================
-- 背景   : 物理表 form_data_{code}_v{version} 随表定义版本增长，旧版本表只读但仍在主库，
--          数据量大后需要把旧版本物理表标记为「已归档」（数据已导出至历史库/对象存储）。
-- 方案   : form_table_registry 增加 archive_status / archive_time 两个运维标记列；
--          本脚本只做结构变更，不做任何数据迁移与物理表删除（归档动作由运维接口标记）。
-- 幂等   : 按 information_schema.COLUMNS 判定后再 ADD COLUMN，可重复执行。
-- =====================================================================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_table_registry'
        AND COLUMN_NAME = 'archive_status') = 0,
    'ALTER TABLE form_table_registry ADD COLUMN archive_status TINYINT NOT NULL DEFAULT 0 COMMENT ''归档状态：0 未归档 / 1 已归档'' AFTER last_error',
    'SELECT 1');
PREPARE form_v12_stmt FROM @ddl;
EXECUTE form_v12_stmt;
DEALLOCATE PREPARE form_v12_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_table_registry'
        AND COLUMN_NAME = 'archive_time') = 0,
    'ALTER TABLE form_table_registry ADD COLUMN archive_time DATETIME DEFAULT NULL COMMENT ''归档标记时间'' AFTER archive_status',
    'SELECT 1');
PREPARE form_v12_stmt FROM @ddl;
EXECUTE form_v12_stmt;
DEALLOCATE PREPARE form_v12_stmt;
