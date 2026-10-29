-- =====================================================================
-- BlueWhale 微服务平台 - whale-agent 文档知识源迁移（Wiki 文档 M4）
-- =====================================================================
-- 幂等说明：全部使用 IF NOT EXISTS，可重复执行。
-- 内容说明：新增文档知识库采集台账 agent_knowledge_doc，
--          仅存页面级采集状态与增量游标，正文知识块仍写入 Lucene。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS agent_knowledge_doc (
    id                 BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离；同步游标按租户独立）',
    page_id            BIGINT        NOT NULL               COMMENT '文档页面ID（doc_page.id）',
    space_id           BIGINT        DEFAULT NULL           COMMENT '文档空间ID',
    title              VARCHAR(255)  DEFAULT NULL           COMMENT '页面标题',
    slug               VARCHAR(128)  DEFAULT NULL           COMMENT '页面路径',
    revision           BIGINT        NOT NULL DEFAULT 0     COMMENT '页面版本号',
    content_hash       VARCHAR(32)   DEFAULT NULL           COMMENT '正文哈希（变化触发重建）',
    url                VARCHAR(512)  DEFAULT NULL           COMMENT '页面地址（检索结果跳转）',
    index_status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/INDEXING/INDEXED/SKIPPED/FAILED',
    chunk_count        INT           NOT NULL DEFAULT 0     COMMENT '已写入知识块数',
    attempts           INT           NOT NULL DEFAULT 0     COMMENT '失败尝试次数',
    next_retry_at      DATETIME      DEFAULT NULL           COMMENT '下次重试时间（指数退避）',
    last_error         VARCHAR(512)  DEFAULT NULL           COMMENT '最近失败原因（截断）',
    indexed_at         DATETIME      DEFAULT NULL           COMMENT '最近成功索引时间',
    enabled            TINYINT       NOT NULL DEFAULT 1     COMMENT '是否启用：1 启用 / 0 停用',
    source_update_time DATETIME      DEFAULT NULL           COMMENT '页面更新时间（增量游标）',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_page_id (page_id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_doc_index_status (index_status),
    KEY idx_doc_source_update (source_update_time, id),
    KEY idx_doc_retry (index_status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文档知识库采集表';
