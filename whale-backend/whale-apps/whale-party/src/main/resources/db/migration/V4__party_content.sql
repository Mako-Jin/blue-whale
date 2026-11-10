-- =====================================================================
-- BlueWhale 党政工会应用（whale-party）M-P2 党建内容与先审后发（Wave 5.88）
-- =====================================================================
-- 范围       : 党建内容 / 审核轨迹 / 阅读记录
-- 需求       : REQ-FUNC-091（党建 M-P2）、REQ-SEC-036（党政工会数据安全）
-- 边界       : 封面与附件只存 whale-file 的 fileId 引用，不存字节、不转码；
--              正文入库以支持审核与检索。员工/组织主数据仍只读引用 ERP。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE / UPDATE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 党建内容（聚合根）
--    status     : DRAFT → PENDING → PUBLISHED → OFFLINE；PENDING 可退回 DRAFT，
--                 PUBLISHED 修改后自动回到 PENDING（先审后发，编辑不即生效）
--    visibility : ALL_MEMBERS（全体党员）/ BRANCHES（指定支部）/ ALL_STAFF（全员）
--    branch_scope : 逗号分隔支部 ID，仅 visibility=BRANCHES 时有效
--    read_count : 按用户去重后的阅读人数（不是页面打开次数）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_content (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    category            VARCHAR(64)   NOT NULL                COMMENT '内容分类',
    title               VARCHAR(200)  NOT NULL                COMMENT '标题',
    summary             VARCHAR(500)  DEFAULT NULL            COMMENT '摘要',
    cover_file_id       BIGINT        DEFAULT NULL            COMMENT '封面文件ID（引用 whale-file）',
    body                MEDIUMTEXT    DEFAULT NULL            COMMENT '正文（草稿可为空，提交审核必填）',
    attachment_file_ids VARCHAR(512)  DEFAULT NULL            COMMENT '附件文件ID列表（逗号分隔，最多 10 个）',
    visibility          VARCHAR(16)   NOT NULL DEFAULT 'ALL_MEMBERS' COMMENT '可见范围：ALL_MEMBERS/BRANCHES/ALL_STAFF',
    branch_scope        VARCHAR(512)  DEFAULT NULL            COMMENT '指定支部ID列表（逗号分隔，仅 BRANCHES 有效）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING/PUBLISHED/OFFLINE',
    author_id           BIGINT        NOT NULL                COMMENT '作者用户ID',
    author_name         VARCHAR(64)   NOT NULL                COMMENT '作者姓名快照',
    submit_time         DATETIME      DEFAULT NULL            COMMENT '最近提交审核时间',
    published_at        DATETIME      DEFAULT NULL            COMMENT '最近发布时间',
    offline_at          DATETIME      DEFAULT NULL            COMMENT '下线时间',
    read_count          INT           NOT NULL DEFAULT 0      COMMENT '阅读人数（按用户去重）',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_pty_content_status (tenant_id, status, create_time),
    KEY idx_pty_content_category (tenant_id, category),
    KEY idx_pty_content_author (tenant_id, author_id),
    KEY idx_pty_content_published (tenant_id, status, published_at)
) COMMENT='党建内容';

-- ---------------------------------------------------------------------
-- 2. 审核轨迹
--    每次状态流转写一条：谁、何时、从什么状态到什么状态、意见是什么。
--    退回意见必填（应用层校验），否则事后只知「被退过」，不知「为什么退」。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_content_audit (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    content_id          BIGINT        NOT NULL                COMMENT '内容ID',
    action              VARCHAR(16)   NOT NULL                COMMENT '动作：SUBMIT/APPROVE/REJECT/OFFLINE/EDIT',
    from_status         VARCHAR(16)   DEFAULT NULL            COMMENT '变更前状态',
    to_status           VARCHAR(16)   DEFAULT NULL            COMMENT '变更后状态',
    opinion             VARCHAR(1000) DEFAULT NULL            COMMENT '审核意见（退回必填）',
    operator_id         BIGINT        NOT NULL                COMMENT '操作人用户ID',
    operator_name       VARCHAR(64)   DEFAULT NULL            COMMENT '操作人姓名快照',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_pty_content_audit_content (tenant_id, content_id, id)
) COMMENT='党建内容审核轨迹';

-- ---------------------------------------------------------------------
-- 3. 阅读记录
--    唯一键 (tenant_id, content_id, user_id)：同一人同一内容只记一条，
--    重复阅读刷新时间而不是插新记录，否则阅读数会被刷新次数放大。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_content_read (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    content_id          BIGINT        NOT NULL                COMMENT '内容ID',
    user_id             BIGINT        NOT NULL                COMMENT '阅读人用户ID',
    user_name           VARCHAR(64)   DEFAULT NULL            COMMENT '阅读人姓名快照',
    read_at             DATETIME      DEFAULT NULL            COMMENT '最近阅读时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_content_read (tenant_id, content_id, user_id),
    KEY idx_pty_content_read_user (tenant_id, user_id)
) COMMENT='党建内容阅读记录';
