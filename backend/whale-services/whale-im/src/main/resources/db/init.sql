-- =====================================================================
-- BlueWhale 微服务平台 - WebIM（whale-im）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_im
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 幂等约定   : CREATE TABLE IF NOT EXISTS，可在既有库上重复执行
-- 说明       : 消息序号 seq 由 Redis INCR 分配后落库；用户/BOT 均为逻辑外键，
--              引用 whale-auth 的 sys_user.id，不建物理外键
-- =====================================================================

-- ---------------------------------------------------------------------
-- 会话表：SINGLE 单聊 / GROUP 群聊 / BOT 机器人会话
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_conversation (
    id              BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    type            VARCHAR(16)  NOT NULL               COMMENT '会话类型：SINGLE 单聊 / GROUP 群聊 / BOT 机器人会话',
    title           VARCHAR(128) DEFAULT NULL           COMMENT '会话标题（群聊必填；单聊由前端按对方展示）',
    owner_id        BIGINT       NOT NULL               COMMENT '会话创建人ID（逻辑关联 sys_user.id）',
    last_seq        BIGINT       NOT NULL DEFAULT 0     COMMENT '当前最大消息序号（发送时递增）',
    last_message_id BIGINT       DEFAULT NULL           COMMENT '最后一条消息ID（逻辑关联 im_message.id）',
    last_active_at  DATETIME     DEFAULT NULL           COMMENT '最后活跃时间（会话列表排序依据）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_owner_id (owner_id),
    KEY idx_last_active_at (last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 会话表';

-- ---------------------------------------------------------------------
-- 会话成员表：成员身份 + 已读游标合一（避免额外已读表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_conversation_member (
    id            BIGINT      NOT NULL               COMMENT '主键ID（雪花算法）',
    conversation_id BIGINT    NOT NULL               COMMENT '会话ID（逻辑关联 im_conversation.id）',
    user_id       BIGINT      NOT NULL               COMMENT '用户ID / BOT 用户ID（逻辑关联 sys_user.id）',
    member_type   VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '成员类型：USER 用户 / BOT 机器人',
    role          VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT '会话角色：OWNER 群主 / ADMIN 管理员 / MEMBER 成员',
    last_read_seq BIGINT      NOT NULL DEFAULT 0     COMMENT '已读游标：该成员已读到的最大消息序号',
    mute_until    DATETIME    DEFAULT NULL           COMMENT '禁言截止时间（NULL 表示未禁言）',
    join_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT     NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_user (conversation_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 会话成员与已读游标表';

-- ---------------------------------------------------------------------
-- 消息表：client_msg_id 唯一索引保证客户端重发幂等
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_message (
    id              BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    conversation_id BIGINT       NOT NULL               COMMENT '会话ID（逻辑关联 im_conversation.id）',
    seq             BIGINT       NOT NULL               COMMENT '会话内单调递增序号（Redis INCR 分配）',
    sender_id       BIGINT       NOT NULL               COMMENT '发送者ID / BOT 用户ID（逻辑关联 sys_user.id）',
    sender_type     VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '发送者类型：USER 用户 / BOT 机器人 / SYSTEM 系统',
    content_type    VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT '内容类型：TEXT / MARKDOWN / IMAGE / FILE / CARD',
    content         TEXT         DEFAULT NULL           COMMENT '消息内容（文本原文或附件/卡片 JSON 引用）',
    client_msg_id   VARCHAR(64)  DEFAULT NULL           COMMENT '客户端消息ID（幂等键；服务端生成消息可为空）',
    reply_to        BIGINT       DEFAULT NULL           COMMENT '被回复消息ID（逻辑关联 im_message.id）',
    status          TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 已撤回',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_client (conversation_id, client_msg_id),
    KEY idx_conversation_seq (conversation_id, seq),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 消息表';

-- ---------------------------------------------------------------------
-- 机器人绑定表：会话 ↔ 机器人 ↔ 智能体场景
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_agent_binding (
    id                    BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    conversation_id       BIGINT       NOT NULL               COMMENT '会话ID（逻辑关联 im_conversation.id）',
    bot_user_id           BIGINT       NOT NULL               COMMENT '机器人用户ID（逻辑关联 sys_user.id）',
    agent_scenario        VARCHAR(32)  NOT NULL DEFAULT 'DEFAULT' COMMENT '智能体场景：DEFAULT / ARCH / DATA / OPS',
    agent_conversation_id VARCHAR(128) DEFAULT NULL           COMMENT '智能体会话ID（透传 whale-agent / whale-ai 的会话标识）',
    enabled               TINYINT      NOT NULL DEFAULT 1     COMMENT '是否启用：1 启用 / 0 停用',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_bot (conversation_id, bot_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 会话与智能体绑定表';

-- ---------------------------------------------------------------------
-- 智能体运行表：一次提问的请求/响应消息与工具轨迹、确认态
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_agent_run (
    id                  BIGINT      NOT NULL               COMMENT '主键ID（雪花算法）',
    conversation_id     BIGINT      NOT NULL               COMMENT '会话ID（逻辑关联 im_conversation.id）',
    request_message_id  BIGINT      DEFAULT NULL           COMMENT '触发本次运行的用户消息ID（逻辑关联 im_message.id）',
    response_message_id BIGINT      DEFAULT NULL           COMMENT '智能体回复消息ID（逻辑关联 im_message.id）',
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '运行状态：PENDING / RUNNING / WAITING_CONFIRM / SUCCESS / FAILED',
    tool_trace_json     JSON        DEFAULT NULL           COMMENT '工具调用轨迹（工具名/参数摘要/结果码/耗时）',
    confirmation_json   JSON        DEFAULT NULL           COMMENT '写操作确认信息（confirmation_id/权限码/参数摘要/过期时间）',
    started_at          DATETIME    DEFAULT NULL           COMMENT '开始时间',
    finished_at         DATETIME    DEFAULT NULL           COMMENT '结束时间',
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT     NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_conversation_id (conversation_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 智能体运行记录表';
