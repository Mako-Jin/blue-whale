-- =====================================================================
-- Wave 5.75 M2 剩余：页面级权限 / 标签 / 反向链接 / 页面动态 / 最近访问 /
--                  评论已读 / 中文全文检索（幂等）
-- =====================================================================
-- 适用库   : bluewhale_doc
-- 执行方式 : mysql -u<user> -p bluewhale_doc < migration-5.75-doc-m2b.sql
-- 幂等说明 : 新增列、全文索引先查 information_schema 再执行；新表用 CREATE TABLE
--            IF NOT EXISTS，可重复执行。
-- 说明     : doc_page.update_time 为秒级精度，AI 变更流使用闭区间游标（见 M4）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. doc_page 增加 is_restricted（页面级权限开关）
-- ---------------------------------------------------------------------
SET @col_restricted := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND column_name = 'is_restricted'
);
SET @ddl_restricted := IF(@col_restricted = 0,
    'ALTER TABLE doc_page ADD COLUMN is_restricted TINYINT NOT NULL DEFAULT 0 COMMENT ''页面级权限：0 继承空间 / 1 仅 ACL 授权用户可见'' AFTER archive_at',
    'SELECT 1');
PREPARE stmt_restricted FROM @ddl_restricted;
EXECUTE stmt_restricted;
DEALLOCATE PREPARE stmt_restricted;

-- ---------------------------------------------------------------------
-- 2. 中文全文检索索引（ngram 解析器，MySQL 内置）
-- ---------------------------------------------------------------------
SET @idx_ft := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'doc_page' AND index_name = 'ft_doc_page_text'
);
SET @ddl_ft := IF(@idx_ft = 0,
    'ALTER TABLE doc_page ADD FULLTEXT KEY ft_doc_page_text (title, content_text) WITH PARSER ngram',
    'SELECT 1');
PREPARE stmt_ft FROM @ddl_ft;
EXECUTE stmt_ft;
DEALLOCATE PREPARE stmt_ft;

-- ---------------------------------------------------------------------
-- 3. 页面级权限
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_acl (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    user_id          BIGINT        NOT NULL                COMMENT '被授权用户ID',
    acl_role         VARCHAR(8)    NOT NULL DEFAULT 'READ' COMMENT '页面角色：READ / EDIT',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_page_acl (tenant_id, page_id, user_id),
    KEY idx_doc_page_acl_user (tenant_id, user_id, deleted)
) COMMENT='文档页面级权限表';

-- ---------------------------------------------------------------------
-- 4. 标签
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_tag (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    space_id         BIGINT        NOT NULL                COMMENT '空间ID（逻辑关联 doc_space.id）',
    name             VARCHAR(64)   NOT NULL                COMMENT '标签名称（空间内唯一）',
    color            VARCHAR(16)   DEFAULT NULL            COMMENT '标签颜色（十六进制）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_tag_name (tenant_id, space_id, name),
    KEY idx_doc_tag_space (tenant_id, space_id, deleted, name)
) COMMENT='文档标签表';

CREATE TABLE IF NOT EXISTS doc_page_tag (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    tag_id           BIGINT        NOT NULL                COMMENT '标签ID（逻辑关联 doc_tag.id）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_page_tag (tenant_id, page_id, tag_id),
    KEY idx_doc_page_tag_tag (tenant_id, tag_id, deleted)
) COMMENT='文档页面标签关联表';

-- ---------------------------------------------------------------------
-- 5. 页面引用关系
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_link (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    source_page_id   BIGINT        NOT NULL                COMMENT '引用方页面ID',
    target_page_id   BIGINT        NOT NULL                COMMENT '被引用页面ID',
    link_text        VARCHAR(200)  DEFAULT NULL            COMMENT '链接文本/锚点摘要',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_doc_link_target (tenant_id, target_page_id, deleted),
    KEY idx_doc_link_source (tenant_id, source_page_id, deleted)
) COMMENT='文档页面引用表';

-- ---------------------------------------------------------------------
-- 6. 页面动态（append-only）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_activity (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    action           VARCHAR(16)   NOT NULL                COMMENT '动作：CREATE/SAVE/PUBLISH/ARCHIVE/RESTORE/MOVE/TRASH/COMMENT/TAG/ACL/IMPORT',
    actor_id         BIGINT        DEFAULT NULL            COMMENT '操作人ID',
    detail           VARCHAR(500)  DEFAULT NULL            COMMENT '动作摘要（不含正文）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_doc_activity_page (tenant_id, page_id, create_time),
    KEY idx_doc_activity_time (tenant_id, create_time)
) COMMENT='文档页面动态表';

-- ---------------------------------------------------------------------
-- 7. 最近访问
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_recent (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id          BIGINT        NOT NULL                COMMENT '访问用户ID',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    visited_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近访问时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_recent (tenant_id, user_id, page_id),
    KEY idx_doc_recent_user (tenant_id, user_id, visited_at)
) COMMENT='文档最近访问表';

-- ---------------------------------------------------------------------
-- 8. 评论已读状态
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_comment_read (
    id                    BIGINT      NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT      NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id               BIGINT      NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    user_id               BIGINT      NOT NULL                COMMENT '用户ID',
    last_read_comment_id  BIGINT      NOT NULL DEFAULT 0      COMMENT '最后已读评论ID',
    last_read_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后已读时间',
    create_time           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_comment_read (tenant_id, page_id, user_id),
    KEY idx_doc_comment_read_user (tenant_id, user_id, last_read_at)
) COMMENT='文档评论已读状态表';

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM doc_page LIKE 'is_restricted';
--   SHOW INDEX FROM doc_page WHERE Key_name = 'ft_doc_page_text';
--   SHOW TABLES LIKE 'doc_%';
--   SELECT 'ngram' FROM information_schema.plugins WHERE plugin_name = 'ngram';
-- =====================================================================
