-- =====================================================================
-- BlueWhale 微服务平台 - 即时通讯（whale-im） Flyway 版本化基线 V1
-- =====================================================================
-- 适用库   : bluewhale_im（MySQL 8.0+，utf8mb4）
-- 来源     : 由 db/init.sql 转换导出（2026-11-01 阶段 4 铺开）
-- 转换规则 : 1) 跳过 DROP TABLE；2) CREATE TABLE 统一 IF NOT EXISTS；
--            3) INSERT 统一 INSERT IGNORE（种子幂等）；4) 表尾统一 ENGINE/CHARSET/COLLATE。
-- 说明     : db/init.sql 为手工空库初始化入口，历史 migration-*.sql 仅供追溯，
--            二者均不在 spring.flyway.locations 扫描路径内（详见 db/README.md）。
-- =====================================================================

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
    tenant_id       BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    type            VARCHAR(16)  NOT NULL               COMMENT '会话类型：SINGLE 单聊 / GROUP 群聊 / BOT 机器人会话',
    title           VARCHAR(128) DEFAULT NULL           COMMENT '会话标题（群聊必填；单聊由前端按对方展示）',
    owner_id        BIGINT       NOT NULL               COMMENT '会话创建人ID（逻辑关联 sys_user.id）',
    pair_key        VARCHAR(64)  DEFAULT NULL           COMMENT '幂等键：SINGLE=min:max 用户对；BOT=B:用户ID；GROUP 为空（唯一索引保证单聊/机器人会话唯一）',
    last_seq        BIGINT       NOT NULL DEFAULT 0     COMMENT '当前最大消息序号（发送时递增）',
    last_message_id BIGINT       DEFAULT NULL           COMMENT '最后一条消息ID（逻辑关联 im_message.id）',
    last_active_at  DATETIME     DEFAULT NULL           COMMENT '最后活跃时间（会话列表排序依据）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_pair_key (tenant_id, pair_key),
    KEY idx_owner_id (owner_id),
    KEY idx_last_active_at (last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 会话表';

-- ---------------------------------------------------------------------
-- 会话成员表：成员身份 + 已读游标合一（避免额外已读表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_conversation_member (
    id            BIGINT      NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT      NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    conversation_id BIGINT    NOT NULL               COMMENT '会话ID（逻辑关联 im_conversation.id）',
    user_id       BIGINT      NOT NULL               COMMENT '用户ID / BOT 用户ID（逻辑关联 sys_user.id）',
    member_type   VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '成员类型：USER 用户 / BOT 机器人',
    role          VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT '会话角色：OWNER 群主 / ADMIN 管理员 / MEMBER 成员',
    last_read_seq BIGINT      NOT NULL DEFAULT 0     COMMENT '已读游标：该成员已读到的最大消息序号',
    mute_until    DATETIME    DEFAULT NULL           COMMENT '禁言截止时间（NULL 表示未禁言）',
    join_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT     NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_user (conversation_id, user_id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 会话成员与已读游标表';

-- ---------------------------------------------------------------------
-- 消息表：client_msg_id 唯一索引保证客户端重发幂等
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_message (
    id              BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
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
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_client (conversation_id, client_msg_id),
    UNIQUE KEY uk_conversation_seq (conversation_id, seq),
    KEY idx_tenant_id (tenant_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 消息表';

-- ---------------------------------------------------------------------
-- 机器人绑定表：会话 ↔ 机器人 ↔ 智能体场景
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_agent_binding (
    id                    BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    conversation_id       BIGINT       NOT NULL               COMMENT '会话ID（逻辑关联 im_conversation.id）',
    bot_user_id           BIGINT       NOT NULL               COMMENT '机器人用户ID（逻辑关联 sys_user.id）',
    agent_scenario        VARCHAR(32)  NOT NULL DEFAULT 'DEFAULT' COMMENT '智能体场景：DEFAULT / ARCH / DATA / OPS',
    agent_conversation_id VARCHAR(128) DEFAULT NULL           COMMENT '智能体会话ID（透传 whale-agent / whale-ai 的会话标识）',
    enabled               TINYINT      NOT NULL DEFAULT 1     COMMENT '是否启用：1 启用 / 0 停用',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_bot (conversation_id, bot_user_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 会话与智能体绑定表';

-- ---------------------------------------------------------------------
-- 智能体运行表：一次提问的请求/响应消息与工具轨迹、确认态
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS im_agent_run (
    id                  BIGINT      NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT      NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    conversation_id     BIGINT      NOT NULL               COMMENT '会话ID（逻辑关联 im_conversation.id）',
    request_message_id  BIGINT      DEFAULT NULL           COMMENT '触发本次运行的用户消息ID（逻辑关联 im_message.id）',
    response_message_id BIGINT      DEFAULT NULL           COMMENT '智能体回复消息ID（逻辑关联 im_message.id）',
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '运行状态：PENDING / RUNNING / WAITING_CONFIRM / SUCCESS / FAILED',
    tool_trace_json     JSON        DEFAULT NULL           COMMENT '工具调用轨迹（工具名/参数摘要/结果码/耗时）',
    confirmation_json   JSON        DEFAULT NULL           COMMENT '写操作确认信息（confirmation_id/权限码/参数摘要/过期时间）',
    feedback            VARCHAR(8)  DEFAULT NULL           COMMENT '机器人回答评价：UP 有用 / DOWN 无用 / NULL 未评价',
    started_at          DATETIME    DEFAULT NULL           COMMENT '开始时间',
    finished_at         DATETIME    DEFAULT NULL           COMMENT '结束时间',
    create_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT     NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_conversation_id (conversation_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IM 智能体运行记录表';

-- ---------------------------------------------------------------------
-- 操作审计日志表（T16：确认操作审计落库）
-- 设计说明：与 whale-audit 同构，append-only（无 update_time / deleted）；
--           id 雪花、create_time 即记录产生时间；由 common AuditLogAspect 异步落库。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（多租户扩展预留）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-im',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述，如 智能体确认平台操作',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 im_agent_run',
    target_id      VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象ID（批量操作为逗号分隔或 JSON 数组）',
    request_params TEXT          NULL                   COMMENT '请求参数（JSON 格式，敏感字段须脱敏后写入）',
    result_body    TEXT          NULL                   COMMENT '返回体（JSON，脱敏 + 截断；recordResult=true 时写入）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息，已脱敏）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '操作IP地址（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即操作发生时间）',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_user_id (user_id),
    KEY idx_module (module),
    KEY idx_operate_type (operate_type),
    KEY idx_biz_status (biz_status),
    KEY idx_tenant_time (tenant_id, create_time),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作审计日志表';

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    query_string   VARCHAR(1024) DEFAULT NULL           COMMENT '查询串（敏感字段已脱敏）',
    request_params TEXT          NULL                   COMMENT '请求体（JSON，敏感字段已脱敏）',
    response_body  TEXT          NULL                   COMMENT '响应体（JSON，敏感字段已脱敏，超限截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码（HTTP 状态）',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '调用方IP（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_user_id (user_id),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_biz_status (biz_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后端接口日志表';
