-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V17：附件引用表补齐公共字段
-- =====================================================================
-- 背景   : V15 建 form_attachment_ref 时只建了 create_time，但 FormAttachmentRefPo
--          继承 BaseEntity（含 create_time / update_time / @TableLogic deleted）。
--          MyBatis-Plus 的 delete / select 因此会带上 `deleted = 0`，
--          在提交表单数据清理旧引用时报 Unknown column 'deleted' in 'where clause'。
-- 方案   : 幂等补齐 update_time 与 deleted 两列，与 V9 form_reconcile_diff 口径一致。
-- 幂等   : 按 information_schema.COLUMNS 判定后再 ADD COLUMN；表不存在时直接跳过。
-- =====================================================================

SET @has_table = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_attachment_ref');

SET @ddl = IF(
    @has_table = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_attachment_ref'
        AND COLUMN_NAME = 'update_time') = 0,
    'ALTER TABLE form_attachment_ref ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER create_time',
    'SELECT 1');
PREPARE form_v17_stmt FROM @ddl;
EXECUTE form_v17_stmt;
DEALLOCATE PREPARE form_v17_stmt;

SET @ddl = IF(
    @has_table = 1
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_attachment_ref'
        AND COLUMN_NAME = 'deleted') = 0,
    'ALTER TABLE form_attachment_ref ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0 未删除 / 1 已删除'' AFTER update_time',
    'SELECT 1');
PREPARE form_v17_stmt FROM @ddl;
EXECUTE form_v17_stmt;
DEALLOCATE PREPARE form_v17_stmt;
