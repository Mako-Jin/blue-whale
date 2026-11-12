-- =====================================================================
-- BlueWhale 微服务平台 - 员工论坛（whale-forum） Flyway V2：核心业务表
-- =====================================================================
-- 适用库   : bluewhale_forum（MySQL 8.0+，utf8mb4）
-- Wave     : 6.43（论坛服务 M0）
-- 依据     : docs/requirements/2026-11-12-forum-2d-functional-spec-v1.3.md §六
-- 表前缀   : fm_（forum），单数 snake_case
--
-- 三条硬规则（改表前先读，改错不会报错，只会在并发下静默出错）：
--   1) 楼层号由 fm_thread.next_floor 条件更新分配，floor 一经分配不回收；
--      二级回复（楼中楼）floor 为 NULL、只记 parent_post_id，且最多两级。
--      uk_fm_post_floor 兜底并发：唯一键冲突即重试，不靠 SELECT MAX(floor)+1。
--   2) 唯一编码（board.code / thread.thread_no）软删时由应用改写为
--      「原编码#D{id}」释放唯一键，否则删除后无法重建同名对象。
--   3) 关联/互动表（fm_reaction）物理删除、不挂 deleted，否则「取消后再点」撞唯一键。
--      append-only 表（编辑历史/处置记录/敏感词命中）不设 update_time/deleted。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 版块表（三级结构：大区 → 版块 → 子版，parent_id 自关联）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_board (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    parent_id        BIGINT        DEFAULT NULL           COMMENT '上级版块ID（NULL 为根/大区）',
    code             VARCHAR(64)   NOT NULL               COMMENT '版块编码（租户内唯一；软删改写为 原编码#D{id}）',
    name             VARCHAR(64)   NOT NULL               COMMENT '版块名称（如 鲸聚贤堂）',
    description      VARCHAR(512)  DEFAULT NULL           COMMENT '版块简介',
    icon             VARCHAR(64)   DEFAULT NULL           COMMENT '图标标识（前端映射）',
    visibility       VARCHAR(16)   NOT NULL DEFAULT 'ALL' COMMENT '可见性：ALL 全员 / ROLE 角色白名单 / DEPT 部门',
    visible_roles    VARCHAR(512)  DEFAULT NULL           COMMENT '可见角色码，逗号分隔（visibility=ROLE 时生效）',
    visible_depts    VARCHAR(512)  DEFAULT NULL           COMMENT '可见部门ID，逗号分隔（visibility=DEPT 时生效）',
    audit_mode       TINYINT       NOT NULL DEFAULT 0     COMMENT '审核模式：0 先发后审 / 1 先审后发',
    post_policy      VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '发帖策略：OPEN 开放 / READONLY 只读 / NOTICE_ONLY 仅管理发',
    sort_no          INT           NOT NULL DEFAULT 0     COMMENT '同级排序号（升序）',
    status           TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：0 停用 / 1 启用',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_board_code (tenant_id, code),
    KEY idx_fm_board_parent (tenant_id, parent_id, sort_no),
    KEY idx_fm_board_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛版块表';

-- ---------------------------------------------------------------------
-- 帖子表（聚合根）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_thread (
    id                BIGINT        NOT NULL              COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1    COMMENT '租户ID（行级隔离）',
    board_id          BIGINT        NOT NULL              COMMENT '所属版块ID（fm_board.id）',
    thread_no         VARCHAR(32)   NOT NULL              COMMENT '帖子编号（FM-yyyyMMdd-XXXX；软删改写为 原编号#D{id}）',
    thread_type       VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT '类型：NORMAL/QA/VOTE/NOTICE/ACTIVITY/ARTICLE',
    title             VARCHAR(255)  NOT NULL              COMMENT '标题（业务上限见配置 max-title-length）',
    content           MEDIUMTEXT    NULL                  COMMENT '正文（已消毒的富文本源码）',
    summary           VARCHAR(512)  DEFAULT NULL          COMMENT '摘要（列表/搜索高亮用，由正文提取）',
    tags              VARCHAR(512)  DEFAULT NULL          COMMENT '标签，逗号分隔（仅选既有标签）',
    author_id         BIGINT        NOT NULL              COMMENT '作者用户ID（匿名帖也恒存，审计/禁言依赖）',
    anonymous         TINYINT       NOT NULL DEFAULT 0    COMMENT '是否匿名展示：0 否 / 1 是（仅展示层匿名）',
    anonymous_no      VARCHAR(8)    DEFAULT NULL          COMMENT '匿名编号（HMAC(tenant|board|user) 取 4 位；同人同版恒定）',
    status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/SCHEDULED/PENDING/PUBLISHED/SOLVED/CLOSED/REJECTED/DELETED',
    pinned            TINYINT       NOT NULL DEFAULT 0    COMMENT '悬榜（置顶）：0 否 / 1 是',
    essence           TINYINT       NOT NULL DEFAULT 0    COMMENT '题金榜（精华）：0 否 / 1 是',
    view_count        INT           NOT NULL DEFAULT 0    COMMENT '浏览数（按人 24h 去重后累加）',
    reply_count       INT           NOT NULL DEFAULT 0    COMMENT '回复数（正文字段计数，每日对账校正）',
    like_count        INT           NOT NULL DEFAULT 0    COMMENT '投花数（条件更新，不为负）',
    collect_count     INT           NOT NULL DEFAULT 0    COMMENT '收藏数',
    next_floor        INT           NOT NULL DEFAULT 1    COMMENT '下一个可用楼层号（条件更新分配，不回收）',
    last_reply_time   DATETIME      DEFAULT NULL          COMMENT '最后回复时间（列表排序用）',
    last_reply_user_id BIGINT       DEFAULT NULL          COMMENT '最后回复人（列表展示用）',
    publish_time      DATETIME      DEFAULT NULL          COMMENT '发布时间（SCHEDULED 到点由 whale-job 抢占发布）',
    audit_remark      VARCHAR(512)  DEFAULT NULL          COMMENT '审核/退回意见（驳回必填）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_thread_no (tenant_id, thread_no),
    KEY idx_fm_thread_board (tenant_id, board_id, status, pinned DESC, publish_time DESC),
    KEY idx_fm_thread_author (tenant_id, author_id, create_time),
    KEY idx_fm_thread_reply (tenant_id, board_id, last_reply_time),
    KEY idx_fm_thread_status (tenant_id, status, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛帖子表';

-- ---------------------------------------------------------------------
-- 帖子编辑历史（append-only：发布态编辑必须留原文快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_thread_edit_history (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    thread_id        BIGINT        NOT NULL               COMMENT '帖子ID（fm_thread.id）',
    editor_id        BIGINT        NOT NULL               COMMENT '编辑人用户ID',
    title_before     VARCHAR(255)  DEFAULT NULL           COMMENT '编辑前标题',
    content_before   MEDIUMTEXT    NULL                   COMMENT '编辑前正文快照',
    reason           VARCHAR(256)  DEFAULT NULL           COMMENT '编辑原因（审核版块退回时必填）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
    PRIMARY KEY (id),
    KEY idx_fm_edit_thread (thread_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='帖子编辑历史表（append-only）';

-- ---------------------------------------------------------------------
-- 楼层表（主帖正文为 1 楼；二级回复 floor 为 NULL 且只记 parent_post_id）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_post (
    id                BIGINT        NOT NULL              COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1    COMMENT '租户ID（行级隔离）',
    thread_id         BIGINT        NOT NULL              COMMENT '帖子ID（fm_thread.id）',
    floor             INT           DEFAULT NULL          COMMENT '楼层号（1..N 由服务端分配，软删不回收；二级回复为 NULL）',
    parent_post_id    BIGINT        DEFAULT NULL          COMMENT '父回复ID（NULL 为一级楼层；最多两级）',
    reply_to_post_id  BIGINT        DEFAULT NULL          COMMENT '引用楼层ID（同帖内，被引用软删后展示占位）',
    author_id         BIGINT        NOT NULL              COMMENT '作者用户ID（匿名回复也恒存）',
    anonymous         TINYINT       NOT NULL DEFAULT 0    COMMENT '是否匿名展示：0 否 / 1 是',
    anonymous_no      VARCHAR(8)    DEFAULT NULL          COMMENT '匿名编号（同人同版恒定）',
    content           MEDIUMTEXT    NULL                  COMMENT '正文（已消毒）',
    like_count        INT           NOT NULL DEFAULT 0    COMMENT '投花数',
    status            VARCHAR(16)   NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED 已发布 / REMOVED 已删除',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_post_floor (tenant_id, thread_id, floor),
    KEY idx_fm_post_thread (thread_id, floor),
    KEY idx_fm_post_parent (parent_post_id),
    KEY idx_fm_post_author (tenant_id, author_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛楼层表';

-- ---------------------------------------------------------------------
-- 互动表（投花/藏帖；取消即物理删除，不挂 deleted）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_reaction (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    user_id          BIGINT        NOT NULL               COMMENT '操作用户ID',
    target_type      VARCHAR(16)   NOT NULL               COMMENT '目标类型：THREAD 帖子 / POST 楼层',
    target_id        BIGINT        NOT NULL               COMMENT '目标ID',
    reaction_type    VARCHAR(16)   NOT NULL               COMMENT '互动类型：LIKE 投花 / COLLECT 藏帖',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_reaction (tenant_id, user_id, target_type, target_id, reaction_type),
    KEY idx_fm_reaction_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛互动表（投花/藏帖）';

-- ---------------------------------------------------------------------
-- 处置与审核轨迹（append-only，申诉与合规追溯的唯一依据）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_moderation_record (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    target_type      VARCHAR(16)   NOT NULL               COMMENT '对象类型：THREAD / POST / USER',
    target_id        BIGINT        NOT NULL               COMMENT '对象ID',
    action           VARCHAR(32)   NOT NULL               COMMENT '动作：AUDIT_PASS/AUDIT_REJECT/PIN/UNPIN/ESSENCE/LOCK/REMOVE/MUTE/BAN 等',
    reason           VARCHAR(512)  DEFAULT NULL           COMMENT '原因/审核意见（驳回必填）',
    operator_id      BIGINT        DEFAULT NULL           COMMENT '操作人用户ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_fm_mod_target (target_type, target_id, create_time),
    KEY idx_fm_mod_operator (operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛处置与审核轨迹表（append-only）';

-- ---------------------------------------------------------------------
-- 举报工单（鸣鼓）：与帖子状态是两条链路，受理不自动改帖状态
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_report (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    target_type      VARCHAR(16)   NOT NULL               COMMENT '对象类型：THREAD / POST / USER',
    target_id        BIGINT        NOT NULL               COMMENT '对象ID',
    reporter_id      BIGINT        NOT NULL               COMMENT '举报人用户ID',
    report_type      VARCHAR(32)   NOT NULL               COMMENT '举报类型：SPAM/ABUSE/PORN/ILLEGAL/OTHER',
    description      VARCHAR(1024) DEFAULT NULL           COMMENT '举报说明',
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/HANDLING/RESOLVED/REJECTED',
    handler_id       BIGINT        DEFAULT NULL           COMMENT '处理人用户ID',
    handle_remark    VARCHAR(512)  DEFAULT NULL           COMMENT '处理意见（REJECTED 只通知举报人）',
    handle_time      DATETIME      DEFAULT NULL           COMMENT '处理时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_fm_report_status (tenant_id, status, create_time),
    KEY idx_fm_report_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛举报工单表';

-- ---------------------------------------------------------------------
-- 敏感词命中留痕（append-only）：词、位置、动作三要素缺一不可
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_sensitive_hit (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    target_type      VARCHAR(16)   NOT NULL               COMMENT '对象类型：THREAD / POST',
    target_id        BIGINT        DEFAULT NULL           COMMENT '对象ID（拦截未落库时为空）',
    user_id          BIGINT        DEFAULT NULL           COMMENT '触发用户ID',
    word             VARCHAR(128)  NOT NULL               COMMENT '命中词',
    position         INT           DEFAULT NULL           COMMENT '命中位置（字符偏移）',
    action           VARCHAR(16)   NOT NULL               COMMENT '处置动作：BLOCK 拦截 / REPLACE 替换 / PENDING 转待审',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '命中时间',
    PRIMARY KEY (id),
    KEY idx_fm_hit_time (tenant_id, create_time),
    KEY idx_fm_hit_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛敏感词命中表（append-only）';
