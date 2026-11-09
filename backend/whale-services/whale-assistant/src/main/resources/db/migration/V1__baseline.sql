-- =====================================================================
-- BlueWhale 微服务平台 - AI 助手平台（whale-assistant） Flyway 基线 V1
-- =====================================================================
-- 适用库   : bluewhale_assistant（MySQL 8.0+，utf8mb4）
-- Wave     : 6.01（AI 助手平台 M0）
-- 说明     : 助手定义 / 会话 / 消息 / 工具授权 / 用量五张核心表。
--            LLM 编排仍在 whale-ai（内网 8092），本库不存模型凭据。
--            建表规则对齐 whale-agent V1：IF NOT EXISTS、雪花主键、
--            租户行级隔离、create_time/update_time/deleted 三件套。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 助手定义表（多助手运营核心）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asst_definition (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    code             VARCHAR(64)   NOT NULL               COMMENT '助手编码（租户内唯一，如 platform-helper）',
    name             VARCHAR(128)  NOT NULL               COMMENT '助手名称',
    avatar           VARCHAR(512)  DEFAULT NULL           COMMENT '头像（whale-file 附件URL）',
    description      VARCHAR(512)  DEFAULT NULL           COMMENT '助手简介',
    system_prompt    MEDIUMTEXT    NULL                   COMMENT '系统提示词（装配进 whale-ai Graph）',
    model_params_json MEDIUMTEXT   NULL                   COMMENT '模型参数JSON（model/temperature/max_tokens 等）',
    knowledge_scope  VARCHAR(255)  DEFAULT NULL           COMMENT '知识范围（透传 whale-agent 检索 scope，空=全部）',
    visibility_json  MEDIUMTEXT    NULL                   COMMENT '可见范围JSON（租户/部门/用户白名单；空=租户内全部可见）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'draft' COMMENT '状态：draft 草稿 / published 已发布 / disabled 已停用',
    created_by       BIGINT        DEFAULT NULL           COMMENT '创建人用户ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI助手定义表';

-- ---------------------------------------------------------------------
-- 助手会话表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asst_session (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    assistant_id     BIGINT        NOT NULL               COMMENT '所属助手ID（asst_definition.id）',
    user_id          BIGINT        NOT NULL               COMMENT '所属用户ID（网关注入 X-User-Id）',
    title            VARCHAR(128)  DEFAULT NULL           COMMENT '会话标题（默认取首条消息前50字）',
    source           VARCHAR(32)   NOT NULL DEFAULT 'platform' COMMENT '来源：platform 工作台 / embed 业务内嵌 / openapi 开放接口',
    channel_ref      VARCHAR(128)  DEFAULT NULL           COMMENT '业务侧会话引用（内嵌方回传标识，可空）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_asst_user (tenant_id, assistant_id, user_id, update_time),
    KEY idx_channel_ref (channel_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI助手会话表';

-- ---------------------------------------------------------------------
-- 助手消息表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asst_message (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    session_id       BIGINT        NOT NULL               COMMENT '所属会话ID（asst_session.id）',
    role             VARCHAR(16)   NOT NULL               COMMENT '角色：user / assistant / system',
    content          MEDIUMTEXT    NULL                   COMMENT '消息正文（Markdown）',
    citations_json   MEDIUMTEXT    NULL                   COMMENT '引用块JSON数组（仅 assistant 消息，SSE done 帧回写）',
    tokens           INT           NOT NULL DEFAULT 0     COMMENT 'token 统计（prompt + completion）',
    elapsed_ms       INT           NOT NULL DEFAULT 0     COMMENT '本条回答耗时（毫秒）',
    degraded         TINYINT       NOT NULL DEFAULT 0     COMMENT '是否降级回答：0 正常 / 1 降级',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_session_id (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI助手消息表';

-- ---------------------------------------------------------------------
-- 助手工具授权表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asst_tool_grant (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    assistant_id     BIGINT        NOT NULL               COMMENT '所属助手ID（asst_definition.id）',
    tool_code        VARCHAR(64)   NOT NULL               COMMENT '工具编码（对齐 whale-ai 工具注册表 /v1/tools）',
    granted          TINYINT       NOT NULL DEFAULT 0     COMMENT '授权状态：0 未授权 / 1 已授权',
    approved_by      BIGINT        DEFAULT NULL           COMMENT '授权审批人用户ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asst_tool (tenant_id, assistant_id, tool_code),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI助手工具授权表';

-- ---------------------------------------------------------------------
-- 助手用量日聚合表（按 租户×助手×天 聚合；用户级明细从 asst_message 聚合）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS asst_usage (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    assistant_id     BIGINT        NOT NULL               COMMENT '所属助手ID（asst_definition.id）',
    stat_date        DATE          NOT NULL               COMMENT '统计日期（Asia/Shanghai）',
    session_count    INT           NOT NULL DEFAULT 0     COMMENT '新增会话数',
    message_count    INT           NOT NULL DEFAULT 0     COMMENT '消息数（user+assistant）',
    prompt_tokens    BIGINT        NOT NULL DEFAULT 0     COMMENT 'prompt token 累计',
    completion_tokens BIGINT       NOT NULL DEFAULT 0     COMMENT 'completion token 累计',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_asst_date (tenant_id, assistant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI助手用量日聚合表';
