-- =====================================================================
-- Wave 5.55 M2：项目管理（whale-pm）工作项域增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_pm
-- 执行方式 : mysql -u<user> -p bluewhale_pm < migration-5.55-m2-workitem.sql
-- 幂等说明 : CREATE TABLE IF NOT EXISTS；可重复执行，不修改既有表与数据。
-- 变更内容 : pm_work_item / pm_work_item_link / pm_comment / pm_attachment / pm_activity。
-- 前置条件 : 已执行 db/init.sql（M1 四表）或 M1 增量脚本。
-- =====================================================================

CREATE TABLE IF NOT EXISTS pm_work_item (
    id                 BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id         BIGINT       NOT NULL            COMMENT '项目ID（逻辑关联 pm_project.id）',
    item_no            INT          NOT NULL            COMMENT '项目内序号（从 1 递增）',
    item_key           VARCHAR(64)  NOT NULL            COMMENT '工作项键（项目键-序号，如 BW-102）',
    type               VARCHAR(16)  NOT NULL            COMMENT '类型：EPIC / STORY / TASK / BUG / SUBTASK',
    title              VARCHAR(255) NOT NULL            COMMENT '标题',
    description        TEXT         DEFAULT NULL        COMMENT '描述（Markdown / 纯文本）',
    status_id          BIGINT       NOT NULL            COMMENT '状态ID（逻辑关联 pm_status.id）',
    priority           VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：HIGHEST / HIGH / MEDIUM / LOW / LOWEST',
    assignee_id        BIGINT       DEFAULT NULL        COMMENT '负责人用户ID',
    reporter_id        BIGINT       DEFAULT NULL        COMMENT '报告人用户ID',
    parent_id          BIGINT       DEFAULT NULL        COMMENT '父工作项ID（子任务必填）',
    sprint_id          BIGINT       DEFAULT NULL        COMMENT '迭代ID（M3 启用）',
    story_points       DECIMAL(5,1) DEFAULT NULL        COMMENT '故事点',
    due_date           DATE         DEFAULT NULL        COMMENT '截止日期',
    labels             VARCHAR(255) DEFAULT NULL        COMMENT '标签（英文逗号分隔）',
    rank_value         VARCHAR(64)  NOT NULL            COMMENT '排序值（12 位定长数字字符串，项目内唯一）',
    extension_json     TEXT         DEFAULT NULL        COMMENT '扩展字段 JSON（首版预留）',
    version            INT          NOT NULL DEFAULT 0  COMMENT '版本号（乐观锁）',
    client_request_id  VARCHAR(64)  DEFAULT NULL        COMMENT '客户端请求ID（创建幂等键）',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_item_no (tenant_id, project_id, item_no),
    UNIQUE KEY uk_pm_item_key (tenant_id, project_id, item_key),
    UNIQUE KEY uk_pm_item_idem (tenant_id, project_id, client_request_id),
    UNIQUE KEY uk_pm_item_rank (tenant_id, project_id, rank_value),
    KEY idx_pm_item_status (tenant_id, project_id, status_id),
    KEY idx_pm_item_assignee (tenant_id, project_id, assignee_id),
    KEY idx_pm_item_parent (tenant_id, project_id, parent_id),
    KEY idx_pm_item_sprint (tenant_id, project_id, sprint_id)
) COMMENT='工作项表';

CREATE TABLE IF NOT EXISTS pm_work_item_link (
    id          BIGINT      NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT      NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id  BIGINT      NOT NULL            COMMENT '项目ID',
    source_id   BIGINT      NOT NULL            COMMENT '源工作项ID',
    target_id   BIGINT      NOT NULL            COMMENT '目标工作项ID',
    link_type   VARCHAR(24) NOT NULL            COMMENT '关联类型：BLOCKS / BLOCKED_BY / RELATES / DUPLICATES / DUPLICATED_BY',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT     NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_link_edge (tenant_id, source_id, target_id, link_type),
    KEY idx_pm_link_project (tenant_id, project_id)
) COMMENT='工作项关联表';

CREATE TABLE IF NOT EXISTS pm_comment (
    id           BIGINT   NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT   NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT   NOT NULL            COMMENT '项目ID',
    work_item_id BIGINT   NOT NULL            COMMENT '工作项ID',
    author_id    BIGINT   NOT NULL            COMMENT '评论人用户ID',
    content      TEXT     NOT NULL            COMMENT '评论内容',
    parent_id    BIGINT   DEFAULT NULL        COMMENT '父评论ID（回复）',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT  NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_pm_comment_item (tenant_id, work_item_id, create_time)
) COMMENT='工作项评论表';

CREATE TABLE IF NOT EXISTS pm_attachment (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT       NOT NULL            COMMENT '项目ID',
    work_item_id BIGINT       NOT NULL            COMMENT '工作项ID',
    file_id      BIGINT       NOT NULL            COMMENT '文件ID（逻辑关联 whale-file）',
    file_name    VARCHAR(255) NOT NULL            COMMENT '文件名（展示用）',
    uploader_id  BIGINT       NOT NULL            COMMENT '上传人用户ID',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_attachment_file (tenant_id, work_item_id, file_id),
    KEY idx_pm_attachment_item (tenant_id, work_item_id, create_time)
) COMMENT='工作项附件表';

CREATE TABLE IF NOT EXISTS pm_activity (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    project_id   BIGINT       NOT NULL            COMMENT '项目ID',
    work_item_id BIGINT       DEFAULT NULL        COMMENT '工作项ID（项目级动态可为空）',
    actor_id     BIGINT       DEFAULT NULL        COMMENT '操作人用户ID',
    action       VARCHAR(32)  NOT NULL            COMMENT '动作：CREATE / UPDATE / TRANSITION / COMMENT / ATTACH / LINK / DELETE',
    field        VARCHAR(64)  DEFAULT NULL        COMMENT '变更字段',
    old_value    VARCHAR(512) DEFAULT NULL        COMMENT '旧值',
    new_value    VARCHAR(512) DEFAULT NULL        COMMENT '新值',
    trace_id     VARCHAR(64)  DEFAULT NULL        COMMENT '链路追踪ID',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_pm_activity_item (tenant_id, work_item_id, create_time),
    KEY idx_pm_activity_project (tenant_id, project_id, create_time)
) COMMENT='工作项动态表';
