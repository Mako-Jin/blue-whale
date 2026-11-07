-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V13
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 里程碑   : Wave 5.82 M5-7d-1 企业文化 / 投票问卷 / 建议箱
-- 说明     : 新建文化动态、生日/周年祝福、投票、选项、投票记录、
--            建议与建议回复共 7 张表；不新增审批配置（本批不走 Workflow）。
--            生日/周年由 oa_employee_profile.birthday / hire_date 派生，
--            culture_visible 控制是否公开展示；匿名建议仅在视图层隐藏提交人。
-- 幂等     : CREATE TABLE IF NOT EXISTS；无种子数据。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 文化动态 / 大事记（草稿 → 发布 → 归档）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_culture_event (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    event_type   VARCHAR(16)  NOT NULL DEFAULT 'ACTIVITY'
                                                  COMMENT '类型：MILESTONE 大事记 / ACTIVITY 活动 / NEWS 动态',
    title        VARCHAR(128) NOT NULL            COMMENT '标题',
    content      TEXT         DEFAULT NULL        COMMENT '正文',
    event_date   DATE         DEFAULT NULL        COMMENT '发生日期',
    cover_file_id BIGINT      DEFAULT NULL        COMMENT '封面文件ID（逻辑关联 whale-file 文件ID）',
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                                  COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布 / ARCHIVED 已归档',
    publisher_id BIGINT       DEFAULT NULL        COMMENT '发布人用户ID（逻辑关联 sys_user.id）',
    publisher_name VARCHAR(64) DEFAULT NULL       COMMENT '发布人姓名快照',
    publish_time DATETIME     DEFAULT NULL        COMMENT '发布时间',
    remark       VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_culture_event_status (tenant_id, status, event_date),
    KEY idx_oa_culture_event_type (tenant_id, event_type, event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 文化动态表';

-- ---------------------------------------------------------------------
-- 2. 生日 / 周年祝福（同一发送人对同一对象同一天同类型唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_culture_wish (
    id             BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    wish_type      VARCHAR(16)  NOT NULL DEFAULT 'BIRTHDAY'
                                                    COMMENT '祝福类型：BIRTHDAY 生日 / ANNIVERSARY 周年',
    target_user_id BIGINT       NOT NULL            COMMENT '祝福对象用户ID（逻辑关联 sys_user.id）',
    target_name    VARCHAR(64)  DEFAULT NULL        COMMENT '祝福对象姓名快照',
    content        VARCHAR(512) NOT NULL            COMMENT '祝福内容',
    sender_id      BIGINT       NOT NULL            COMMENT '发送人用户ID（逻辑关联 sys_user.id）',
    sender_name    VARCHAR(64)  DEFAULT NULL        COMMENT '发送人姓名快照',
    wish_date      DATE         NOT NULL            COMMENT '祝福日期（生日/周年当天）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_culture_wish_once (tenant_id, wish_type, target_user_id, sender_id, wish_date),
    KEY idx_oa_culture_wish_target (tenant_id, target_user_id, wish_date),
    KEY idx_oa_culture_wish_date (tenant_id, wish_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 文化祝福表';

-- ---------------------------------------------------------------------
-- 3. 投票问卷（草稿 → 发布 → 关闭）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_poll (
    id             BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    title          VARCHAR(128) NOT NULL            COMMENT '投票标题',
    description    TEXT         DEFAULT NULL        COMMENT '投票说明',
    multiple_choice TINYINT     NOT NULL DEFAULT 0  COMMENT '是否多选：0 单选 / 1 多选',
    anonymous      TINYINT      NOT NULL DEFAULT 0  COMMENT '是否匿名：0 实名 / 1 匿名展示',
    max_choices    INT          NOT NULL DEFAULT 1  COMMENT '最多可选数量（单选固定 1）',
    start_time     DATETIME     DEFAULT NULL        COMMENT '开始时间',
    end_time       DATETIME     DEFAULT NULL        COMMENT '结束时间',
    status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                                    COMMENT '状态：DRAFT 草稿 / PUBLISHED 进行中 / CLOSED 已关闭',
    creator_id     BIGINT       NOT NULL            COMMENT '创建人用户ID（逻辑关联 sys_user.id）',
    creator_name   VARCHAR(64)  DEFAULT NULL        COMMENT '创建人姓名快照',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_poll_status (tenant_id, status, start_time),
    KEY idx_oa_poll_creator (tenant_id, creator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 投票问卷表';

-- ---------------------------------------------------------------------
-- 4. 投票选项（同一投票内顺序展示）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_poll_option (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    poll_id     BIGINT       NOT NULL            COMMENT '投票ID（逻辑关联 oa_poll.id）',
    option_text VARCHAR(255) NOT NULL            COMMENT '选项文本',
    sort_no     INT          NOT NULL DEFAULT 0  COMMENT '排序号',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_poll_option_poll (tenant_id, poll_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 投票选项表';

-- ---------------------------------------------------------------------
-- 5. 投票记录（同一用户对同一选项仅一条；多选为多行）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_poll_vote (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    poll_id     BIGINT       NOT NULL            COMMENT '投票ID（逻辑关联 oa_poll.id）',
    option_id   BIGINT       NOT NULL            COMMENT '选项ID（逻辑关联 oa_poll_option.id）',
    user_id     BIGINT       NOT NULL            COMMENT '投票人用户ID（逻辑关联 sys_user.id）',
    user_name   VARCHAR(64)  DEFAULT NULL        COMMENT '投票人姓名快照',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_poll_vote_option (tenant_id, poll_id, user_id, option_id),
    KEY idx_oa_poll_vote_poll (tenant_id, poll_id, option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 投票记录表';

-- ---------------------------------------------------------------------
-- 6. 建议箱（匿名仅视图层隐藏提交人，库内保留用于审计）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_suggestion (
    id             BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    title          VARCHAR(128) NOT NULL            COMMENT '建议标题',
    category       VARCHAR(16)  NOT NULL DEFAULT 'SUGGESTION'
                                                    COMMENT '类别：SUGGESTION 建议 / COMPLAINT 投诉 / PRAISE 表扬 / OTHER 其他',
    content        TEXT         NOT NULL            COMMENT '建议内容',
    anonymous      TINYINT      NOT NULL DEFAULT 0  COMMENT '是否匿名：0 实名 / 1 匿名',
    submitter_id   BIGINT       NOT NULL            COMMENT '提交人用户ID（匿名时仍保留，用于审计）',
    submitter_name VARCHAR(64)  DEFAULT NULL        COMMENT '提交人姓名快照',
    dept_id        BIGINT       DEFAULT NULL        COMMENT '提交人部门ID（逻辑关联 sys_dept.id）',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                    COMMENT '状态：PENDING 待处理 / PROCESSING 处理中 / REPLIED 已回复 / CLOSED 已关闭',
    handler_id     BIGINT       DEFAULT NULL        COMMENT '处理人用户ID',
    handler_name   VARCHAR(64)  DEFAULT NULL        COMMENT '处理人姓名快照',
    handled_time   DATETIME     DEFAULT NULL        COMMENT '最近处理时间',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_suggestion_status (tenant_id, status, create_time),
    KEY idx_oa_suggestion_submitter (tenant_id, submitter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 建议箱表';

-- ---------------------------------------------------------------------
-- 7. 建议回复
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_suggestion_reply (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    suggestion_id BIGINT       NOT NULL            COMMENT '建议ID（逻辑关联 oa_suggestion.id）',
    replier_id    BIGINT       NOT NULL            COMMENT '回复人用户ID',
    replier_name  VARCHAR(64)  DEFAULT NULL        COMMENT '回复人姓名快照',
    content       TEXT         NOT NULL            COMMENT '回复内容',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_suggestion_reply_suggestion (tenant_id, suggestion_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 建议回复表';
