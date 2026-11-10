-- =====================================================================
-- BlueWhale 微服务平台 - 在线文档 / Wiki 服务（whale-doc）M2 增量脚本
-- =====================================================================
-- 库名       : bluewhale_doc
-- 版本       : M2（回收站 + 实时协作）
-- 幂等约定   : 基于 information_schema 判断后执行 DDL，可重复执行
-- 说明       : 仅新增回收站所需字段与索引；实时协作使用 Redis，不改表结构
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. doc_page 回收站字段：deleted_at / deleted_by / trash_root_id
--    trash_root_id 指向本次子树删除的根页面（根页面自身等于 id）
-- ---------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN deleted_at DATETIME NULL COMMENT ''移入回收站时间'' AFTER deleted',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'deleted_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN deleted_by BIGINT NULL COMMENT ''移入回收站操作人ID'' AFTER deleted_at',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'deleted_by'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN trash_root_id BIGINT NULL COMMENT ''回收站根页面ID（根页面等于 id）'' AFTER deleted_by',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'trash_root_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 回收站列表索引：按空间过滤回收站根节点并按删除时间倒序
-- ---------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_doc_page_recycle ON doc_page (tenant_id, space_id, deleted, deleted_at)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND index_name = 'idx_doc_page_recycle'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
