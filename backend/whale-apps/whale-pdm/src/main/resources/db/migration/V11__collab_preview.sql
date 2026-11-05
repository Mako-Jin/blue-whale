-- =====================================================================
-- Wave 5.80 M4：项目交付物、评审记录与在线预览任务
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 新表 CREATE TABLE IF NOT EXISTS；本脚本不含 DROP / TRUNCATE / DELETE。
-- 边界     : 项目/任务本体归 whale-pm，PDM 只保存引用与已发布对象版本；
--            预览产物仍保存在 whale-file，PDM 只记录任务状态与产物 fileId。
-- 状态机   : 交付物 ARCHIVED；评审 DRAFT → CLOSED（结论 PASSED/CONDITIONAL/REJECTED）；
--            预览 PENDING/RUNNING/SUCCESS/FAILED/UNSUPPORTED
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 项目交付物（只引用已发布文档/BOM/工艺路线版本）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_clb_deliverable (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id      BIGINT       NOT NULL COMMENT 'whale-pm 项目ID',
    project_name    VARCHAR(128) NULL COMMENT '项目名称快照',
    work_item_id    BIGINT       NULL COMMENT 'whale-pm 任务ID',
    work_item_name  VARCHAR(128) NULL COMMENT '任务名称快照',
    object_type     VARCHAR(16)  NOT NULL COMMENT '对象类型：DOCUMENT/BOM/ROUTE',
    object_id       BIGINT       NOT NULL COMMENT '对象ID',
    object_no       VARCHAR(64)  NULL COMMENT '对象编号快照',
    object_title    VARCHAR(255) NULL COMMENT '对象名称/标题快照',
    object_version_no VARCHAR(32) NOT NULL COMMENT '已发布对象版本号',
    deliverable_type VARCHAR(16) NOT NULL DEFAULT 'OTHER' COMMENT '交付物类型：DESIGN/DRAWING/REPORT/OTHER',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ARCHIVED' COMMENT '状态：ARCHIVED',
    archived_by     BIGINT       NULL COMMENT '归档人',
    archived_at     DATETIME     NULL COMMENT '归档时间',
    remark          VARCHAR(255) NULL COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_clb_deliverable (tenant_id, project_id, object_type, object_id, object_version_no),
    KEY idx_pdm_clb_deliverable_project (tenant_id, project_id),
    KEY idx_pdm_clb_deliverable_object (tenant_id, object_type, object_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 项目交付物归档';

-- ---------------------------------------------------------------------
-- 2. 评审记录（设计/样机/转产）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_clb_review (
    id                    BIGINT       NOT NULL COMMENT '主键',
    tenant_id             BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    review_no             VARCHAR(32)  NOT NULL COMMENT '评审编号（租户内唯一）',
    review_type           VARCHAR(16)  NOT NULL COMMENT '类型：DESIGN/PROTOTYPE/TRANSFER',
    title                 VARCHAR(255) NOT NULL COMMENT '评审主题',
    project_id            BIGINT       NULL COMMENT 'whale-pm 项目ID',
    project_name          VARCHAR(128) NULL COMMENT '项目名称快照',
    object_type           VARCHAR(16)  NULL COMMENT '评审对象类型：DOCUMENT/BOM/ROUTE/PART',
    object_id             BIGINT       NULL COMMENT '评审对象ID',
    object_no             VARCHAR(64)  NULL COMMENT '评审对象编号快照',
    object_version_no     VARCHAR(32)  NULL COMMENT '评审对象版本号',
    participants          VARCHAR(1000) NULL COMMENT '参与人，逗号分隔',
    conclusion            VARCHAR(16)  NULL COMMENT '结论：PASSED/CONDITIONAL/REJECTED',
    conclusion_comment    VARCHAR(500) NULL COMMENT '结论说明',
    attachment_document_id BIGINT      NULL COMMENT '附件文档ID',
    attachment_version_no VARCHAR(32)  NULL COMMENT '附件文档版本号',
    approval_instance_id  BIGINT       NULL COMMENT 'OA 审批实例ID快照',
    status                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/CLOSED',
    created_by            BIGINT       NULL COMMENT '创建人',
    closed_by             BIGINT       NULL COMMENT '关闭人',
    closed_at             DATETIME     NULL COMMENT '关闭时间',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_clb_review_no (tenant_id, review_no),
    KEY idx_pdm_clb_review_project (tenant_id, project_id),
    KEY idx_pdm_clb_review_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 研发评审记录';

-- ---------------------------------------------------------------------
-- 3. 在线预览任务（幂等键 object:version:type）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_doc_preview_job (
    id                   BIGINT       NOT NULL COMMENT '主键',
    tenant_id            BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    document_id          BIGINT       NOT NULL COMMENT '文档ID',
    version_id           BIGINT       NOT NULL COMMENT '版本ID',
    version_no           VARCHAR(32)  NOT NULL COMMENT '版本号快照',
    preview_type         VARCHAR(8)   NOT NULL COMMENT '预览类型：2D/3D',
    source_file_name     VARCHAR(255) NULL COMMENT '源文件名',
    source_content_type  VARCHAR(128) NULL COMMENT '源内容类型',
    target_format        VARCHAR(16)  NULL COMMENT '目标格式：ORIGINAL/PDF/GLTF 等',
    viewer_hint          VARCHAR(16)  NULL COMMENT '前端查看器提示：PDF/IMAGE/STL/GLTF',
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/UNSUPPORTED',
    retry_count          INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error           VARCHAR(500) NULL COMMENT '最近失败原因',
    artifact_file_id     BIGINT       NULL COMMENT '预览产物 fileId（原生格式为空）',
    artifact_content_type VARCHAR(128) NULL COMMENT '预览产物内容类型',
    artifact_size        BIGINT       NULL COMMENT '预览产物大小',
    started_at           DATETIME     NULL COMMENT '开始时间',
    finished_at          DATETIME     NULL COMMENT '完成时间',
    requested_by         BIGINT       NULL COMMENT '请求人',
    idempotency_key      VARCHAR(96)  NOT NULL COMMENT '幂等键（documentId:versionId:previewType）',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_doc_preview_key (tenant_id, idempotency_key),
    KEY idx_pdm_doc_preview_doc (tenant_id, document_id, version_id),
    KEY idx_pdm_doc_preview_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 在线预览任务';

-- =====================================================================
-- 完成。校验建议：
--   SELECT table_name FROM information_schema.tables
--   WHERE table_schema = DATABASE()
--     AND table_name IN ('pdm_clb_deliverable', 'pdm_clb_review', 'pdm_doc_preview_job');
--   -- 期望：3 行。
-- =====================================================================
