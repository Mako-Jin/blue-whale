-- =====================================================================
-- BlueWhale whale-agent 增量迁移脚本（Wave 5.53 M4：文件知识库）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、bluewhale_agent 库中已存在业务表的运行库。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已包含同构表）。
-- 说明     : 本脚本幂等（CREATE TABLE IF NOT EXISTS）。
--            新增 agent_knowledge_file：文件服务业务文件的采集台账与状态机；
--            file_id 唯一保证幂等，source_update_time 兼作增量变更游标。
-- =====================================================================

CREATE TABLE IF NOT EXISTS agent_knowledge_file (
    id                 BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    file_id            BIGINT        NOT NULL               COMMENT '文件服务文件ID（file_info.id）',
    bucket             VARCHAR(64)   DEFAULT NULL           COMMENT '对象存储桶',
    original_name      VARCHAR(255)  DEFAULT NULL           COMMENT '原始文件名',
    content_type       VARCHAR(128)  DEFAULT NULL           COMMENT '文件MIME类型',
    file_size          BIGINT        NOT NULL DEFAULT 0     COMMENT '文件大小（字节）',
    md5                VARCHAR(32)   DEFAULT NULL           COMMENT '文件MD5摘要（变化触发重建）',
    uploader_id        BIGINT        DEFAULT NULL           COMMENT '上传用户ID',
    source             VARCHAR(16)   NOT NULL DEFAULT 'FILE' COMMENT '知识来源：FILE 业务文件 / ARCHIVE 档案快照',
    index_status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/INDEXING/INDEXED/SKIPPED/FAILED',
    chunk_count        INT           NOT NULL DEFAULT 0     COMMENT '已写入知识块数',
    attempts           INT           NOT NULL DEFAULT 0     COMMENT '失败尝试次数',
    next_retry_at      DATETIME      DEFAULT NULL           COMMENT '下次重试时间（指数退避）',
    last_error         VARCHAR(512)  DEFAULT NULL           COMMENT '最近失败原因（截断）',
    indexed_at         DATETIME      DEFAULT NULL           COMMENT '最近成功索引时间',
    enabled            TINYINT       NOT NULL DEFAULT 1     COMMENT '是否启用：1 启用 / 0 停用',
    source_update_time DATETIME      DEFAULT NULL           COMMENT '文件更新时间（增量游标）',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_id (file_id),
    KEY idx_index_status (index_status),
    KEY idx_source_update (source_update_time, id),
    KEY idx_retry (index_status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件知识库采集表';
