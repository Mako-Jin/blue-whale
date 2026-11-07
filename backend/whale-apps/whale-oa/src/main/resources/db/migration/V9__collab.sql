-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V9
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 上级计划 : docs/plans/2026-11-04-wave5.82-oa-panorama.md
-- 里程碑   : Wave 5.82 M3-P1 协同办公底座（会议 / 公告 / 任务）
-- 说明     : 新建会议室、会议、参会人、会议纪要、行动项、公告、公告接收人、
--            个人任务、任务评论共 9 张表；不新增审批配置（协同业务不走 Workflow）。
-- 幂等     : CREATE TABLE IF NOT EXISTS；无种子数据。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 会议室主数据（名称租户内唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_meeting_room (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    name        VARCHAR(64)  NOT NULL            COMMENT '会议室名称（租户内唯一）',
    location    VARCHAR(128) DEFAULT NULL        COMMENT '位置',
    capacity    INT          NOT NULL DEFAULT 10 COMMENT '容纳人数',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED'
                                                 COMMENT '状态：ENABLED 启用 / DISABLED 停用',
    remark      VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_meeting_room_name (tenant_id, name),
    KEY idx_oa_meeting_room_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 会议室表';

-- ---------------------------------------------------------------------
-- 2. 会议（单号租户内唯一；状态机 SCHEDULED → FINISHED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_meeting (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code         VARCHAR(64)  NOT NULL            COMMENT '会议单号（租户内唯一）',
    title        VARCHAR(128) NOT NULL            COMMENT '会议主题',
    room_id      BIGINT       DEFAULT NULL        COMMENT '会议室ID（逻辑关联 oa_meeting_room.id）',
    organizer    VARCHAR(64)  NOT NULL            COMMENT '组织人账号',
    organizer_id BIGINT       DEFAULT NULL        COMMENT '组织人用户ID（逻辑关联 sys_user.id）',
    dept_id      BIGINT       DEFAULT NULL        COMMENT '组织部门ID',
    start_time   DATETIME     NOT NULL            COMMENT '会议开始时间',
    end_time     DATETIME     NOT NULL            COMMENT '会议结束时间',
    agenda       TEXT         DEFAULT NULL        COMMENT '会议议程',
    status       VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULED'
                                                  COMMENT '状态：SCHEDULED 已安排 / FINISHED 已结束 / CANCELLED 已取消',
    remark       VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_meeting_code (tenant_id, code),
    KEY idx_oa_meeting_room_time (tenant_id, room_id, start_time, end_time),
    KEY idx_oa_meeting_status_time (tenant_id, status, start_time),
    KEY idx_oa_meeting_organizer (tenant_id, organizer_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 会议表';

-- ---------------------------------------------------------------------
-- 3. 参会人（同一会议内用户唯一；状态为参会快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_meeting_participant (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    meeting_id  BIGINT       NOT NULL            COMMENT '会议ID（逻辑关联 oa_meeting.id）',
    user_id     BIGINT       NOT NULL            COMMENT '参会人用户ID（逻辑关联 sys_user.id）',
    user_name   VARCHAR(64)  DEFAULT NULL        COMMENT '参会人姓名快照',
    required    TINYINT      NOT NULL DEFAULT 1  COMMENT '是否必到：0 否 / 1 是',
    status      VARCHAR(16)  NOT NULL DEFAULT 'INVITED'
                                                  COMMENT '参会状态：INVITED 已邀请 / ATTENDED 已出席 / ABSENT 缺席',
    remark      VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_meeting_participant_user (tenant_id, meeting_id, user_id),
    KEY idx_oa_meeting_participant_meeting (tenant_id, meeting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 会议参会人表';

-- ---------------------------------------------------------------------
-- 4. 会议纪要（每场会议最多一份）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_meeting_minute (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    meeting_id  BIGINT       NOT NULL            COMMENT '会议ID（逻辑关联 oa_meeting.id）',
    content     TEXT         NOT NULL            COMMENT '纪要内容',
    author      VARCHAR(64)  DEFAULT NULL        COMMENT '纪要作者账号',
    author_id   BIGINT       DEFAULT NULL        COMMENT '纪要作者用户ID',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_meeting_minute_meeting (tenant_id, meeting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 会议纪要表';

-- ---------------------------------------------------------------------
-- 5. 会议行动项（可生成个人任务，task_id 回填）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_meeting_action (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    meeting_id  BIGINT       NOT NULL            COMMENT '会议ID（逻辑关联 oa_meeting.id）',
    content     VARCHAR(255) NOT NULL            COMMENT '行动项内容',
    owner_id    BIGINT       DEFAULT NULL        COMMENT '负责人用户ID',
    owner_name  VARCHAR(64)  DEFAULT NULL        COMMENT '负责人姓名快照',
    due_date    DATE         DEFAULT NULL        COMMENT '截止日期',
    status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN'
                                                  COMMENT '状态：OPEN 未完成 / DONE 已完成',
    task_id     BIGINT       DEFAULT NULL        COMMENT '生成的个人任务ID（逻辑关联 oa_task.id）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_meeting_action_meeting (tenant_id, meeting_id),
    KEY idx_oa_meeting_action_owner (tenant_id, owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 会议行动项表';

-- ---------------------------------------------------------------------
-- 6. 公告（全员 / 部门 / 指定用户；DRAFT → PUBLISHED → WITHDRAWN）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_notice (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    title        VARCHAR(128) NOT NULL            COMMENT '公告标题',
    content      TEXT         NOT NULL            COMMENT '公告正文',
    scope_type   VARCHAR(16)  NOT NULL DEFAULT 'ALL'
                                                  COMMENT '发布范围：ALL 全员 / DEPT 部门 / USER 指定用户',
    scope_value  VARCHAR(255) DEFAULT NULL        COMMENT '范围值（部门ID或部门名称）',
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                                  COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布 / WITHDRAWN 已撤回',
    publisher    VARCHAR(64)  DEFAULT NULL        COMMENT '发布人账号',
    publisher_id BIGINT       DEFAULT NULL        COMMENT '发布人用户ID',
    publish_time DATETIME     DEFAULT NULL        COMMENT '发布时间',
    remark       VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_notice_status_time (tenant_id, status, publish_time),
    KEY idx_oa_notice_publisher (tenant_id, publisher_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 公告表';

-- ---------------------------------------------------------------------
-- 7. 公告接收人快照（已读统计口径）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_notice_recipient (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    notice_id   BIGINT       NOT NULL            COMMENT '公告ID（逻辑关联 oa_notice.id）',
    user_id     BIGINT       NOT NULL            COMMENT '接收人用户ID（逻辑关联 sys_user.id）',
    user_name   VARCHAR(64)  DEFAULT NULL        COMMENT '接收人姓名快照',
    read_flag   TINYINT      NOT NULL DEFAULT 0  COMMENT '是否已读：0 未读 / 1 已读',
    read_time   DATETIME     DEFAULT NULL        COMMENT '阅读时间',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_notice_recipient_user (tenant_id, notice_id, user_id),
    KEY idx_oa_notice_recipient_notice (tenant_id, notice_id, read_flag),
    KEY idx_oa_notice_recipient_user_read (tenant_id, user_id, read_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 公告接收人表';

-- ---------------------------------------------------------------------
-- 8. 个人任务（来源：个人 / 会议行动项 / 公告）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_task (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    title        VARCHAR(128) NOT NULL            COMMENT '任务标题',
    description  TEXT         DEFAULT NULL        COMMENT '任务描述',
    owner_id     BIGINT       NOT NULL            COMMENT '负责人用户ID（逻辑关联 sys_user.id）',
    owner_name   VARCHAR(64)  DEFAULT NULL        COMMENT '负责人姓名快照',
    creator_id   BIGINT       DEFAULT NULL        COMMENT '创建人用户ID',
    creator_name VARCHAR(64)  DEFAULT NULL        COMMENT '创建人姓名快照',
    priority     VARCHAR(16)  NOT NULL DEFAULT 'NORMAL'
                                                  COMMENT '优先级：HIGH 高 / NORMAL 普通 / LOW 低',
    status       VARCHAR(16)  NOT NULL DEFAULT 'TODO'
                                                  COMMENT '状态：TODO 待办 / DOING 进行中 / DONE 已完成 / CANCELLED 已取消',
    due_date     DATE         DEFAULT NULL        COMMENT '截止日期',
    source       VARCHAR(16)  NOT NULL DEFAULT 'PERSONAL'
                                                  COMMENT '来源：PERSONAL 个人 / MEETING 会议行动项 / NOTICE 公告',
    source_id    BIGINT       DEFAULT NULL        COMMENT '来源业务ID',
    finish_time  DATETIME     DEFAULT NULL        COMMENT '完成时间',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_task_owner_status (tenant_id, owner_id, status, due_date),
    KEY idx_oa_task_source (tenant_id, source, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 个人任务表';

-- ---------------------------------------------------------------------
-- 9. 任务评论
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_task_comment (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    task_id     BIGINT       NOT NULL            COMMENT '任务ID（逻辑关联 oa_task.id）',
    user_id     BIGINT       DEFAULT NULL        COMMENT '评论人用户ID',
    user_name   VARCHAR(64)  DEFAULT NULL        COMMENT '评论人姓名快照',
    content     VARCHAR(500) NOT NULL            COMMENT '评论内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_task_comment_task (tenant_id, task_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 任务评论表';

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM oa_meeting;
--   SHOW COLUMNS FROM oa_notice;
--   SHOW COLUMNS FROM oa_task;
--   SELECT COUNT(*) FROM information_schema.tables
--   WHERE table_schema = 'bluewhale_oa' AND table_name LIKE 'oa_%collab%';
-- =====================================================================
