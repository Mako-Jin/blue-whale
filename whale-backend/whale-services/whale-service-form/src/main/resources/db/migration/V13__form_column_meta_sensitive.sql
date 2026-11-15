-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V13：列敏感标记
-- =====================================================================
-- 背景   : 导出 CSV/XLSX 时对身份证、手机号等敏感字段需要脱敏，当前无字段级敏感标记。
-- 方案   : form_column_meta 增加 sensitive（0 普通 / 1 敏感）；发布编译快照时写入字段节点，
--          导出按字段标记做掩码，入库与详情展示保持原值（仅导出脱敏，避免破坏业务读取）。
-- 幂等   : 按 information_schema.COLUMNS 判定后再 ADD COLUMN，可重复执行。
-- =====================================================================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_column_meta'
        AND COLUMN_NAME = 'sensitive') = 0,
    'ALTER TABLE form_column_meta ADD COLUMN sensitive TINYINT NOT NULL DEFAULT 0 COMMENT ''敏感字段：0 普通 / 1 敏感（导出脱敏）'' AFTER required',
    'SELECT 1');
PREPARE form_v13_stmt FROM @ddl;
EXECUTE form_v13_stmt;
DEALLOCATE PREPARE form_v13_stmt;
