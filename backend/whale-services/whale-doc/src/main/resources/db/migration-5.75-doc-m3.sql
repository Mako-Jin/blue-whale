-- =====================================================================
-- BlueWhale 微服务平台 - 在线文档 / Wiki 服务（whale-doc）M3 增量脚本
-- =====================================================================
-- 库名       : bluewhale_doc
-- 版本       : M3（模板 / 复制 / 导入导出 / 发布审批 / 分享 / 归档）
-- 幂等约定   : 基于 information_schema 判断后执行 DDL，可重复执行
-- 说明       : 新增模板表、分享表与页面审批/归档追溯字段；不修改既有数据
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. doc_template 模板表
--    space_id=0 表示租户级模板；status：1 启用 / 0 停用
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_template (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    space_id         BIGINT        NOT NULL DEFAULT 0      COMMENT '归属空间ID，0 表示租户级模板',
    name             VARCHAR(100)  NOT NULL                COMMENT '模板名称',
    description      VARCHAR(500)  DEFAULT NULL            COMMENT '模板说明',
    page_type        VARCHAR(8)    NOT NULL DEFAULT 'PAGE' COMMENT '节点类型：PAGE / FOLDER',
    icon             VARCHAR(64)   DEFAULT NULL            COMMENT '图标标识',
    content_json     LONGTEXT      DEFAULT NULL            COMMENT '模板正文 JSON（TipTap / ProseMirror）',
    content_text     LONGTEXT      DEFAULT NULL            COMMENT '服务端提取的纯文本投影',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    sort_no          INT           NOT NULL DEFAULT 0      COMMENT '排序',
    author_id        BIGINT        DEFAULT NULL            COMMENT '创建人ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_doc_template_space (tenant_id, space_id, status, deleted, sort_no),
    KEY idx_doc_template_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档模板表';

-- ---------------------------------------------------------------------
-- 2. doc_page_share 登录内分享表
--    share_token 为不可猜随机串；status：1 有效 / 0 已撤销
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_share (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    share_token      VARCHAR(64)   NOT NULL                COMMENT '分享令牌（不可猜随机串）',
    share_role       VARCHAR(16)   NOT NULL DEFAULT 'READER' COMMENT '分享角色：READER',
    expires_at       DATETIME      DEFAULT NULL            COMMENT '过期时间，NULL 表示不限期',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 有效 / 0 已撤销',
    created_by       BIGINT        NOT NULL                COMMENT '创建人ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_share_token (tenant_id, share_token),
    KEY idx_doc_share_page (tenant_id, page_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档页面分享表';

-- ---------------------------------------------------------------------
-- 3. doc_page 发布审批与归档追溯字段
-- ---------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN approval_instance_id BIGINT NULL COMMENT ''M3 流程实例ID'' AFTER archived_at',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'approval_instance_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN approval_status VARCHAR(16) NULL COMMENT ''M3 审批状态：PENDING / APPROVED / REJECTED'' AFTER approval_instance_id',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'approval_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN approval_submitted_at DATETIME NULL COMMENT ''M3 审批提交时间'' AFTER approval_status',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'approval_submitted_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN archive_item_id VARCHAR(64) NULL COMMENT ''M3 档案条目ID'' AFTER approval_submitted_at',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'archive_item_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN archive_file_id BIGINT NULL COMMENT ''M3 归档快照文件ID'' AFTER archive_item_id',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'archive_file_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE doc_page ADD COLUMN archive_at DATETIME NULL COMMENT ''M3 归档入档时间'' AFTER archive_file_id',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'archive_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 待审批页面索引：供审批状态轮询与后台同步
-- ---------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_doc_page_approval ON doc_page (tenant_id, status, approval_instance_id)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND index_name = 'idx_doc_page_approval'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'doc_template';
--   SHOW TABLES LIKE 'doc_page_share';
--   SELECT column_name FROM information_schema.columns
--    WHERE table_schema = DATABASE() AND table_name = 'doc_page'
--      AND column_name IN ('approval_instance_id','approval_status','approval_submitted_at',
--                          'archive_item_id','archive_file_id','archive_at');
-- =====================================================================
