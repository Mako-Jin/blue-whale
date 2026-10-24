-- =====================================================================
-- BlueWhale 微服务平台 - 平台与项目知识 Agent（whale-agent）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_agent
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 说明       : 向量检索走进程内 Lucene（data/agent-index/），本库仅存会话、事实与索引元数据
-- =====================================================================

-- ---------------------------------------------------------------------
-- 结构化事实表（双通道回答的「事实通道」）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS agent_fact;
CREATE TABLE agent_fact (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    fact_type       VARCHAR(32)   NOT NULL               COMMENT '事实类型：SERVICE/ROUTE/PORT/DEPENDENCY/TABLE/COLUMN/COMMIT/ENDPOINT/RUNTIME_SERVICE',
    fact_key        VARCHAR(255)  NOT NULL               COMMENT '事实键（服务名/路由id/表名/groupId:artifactId 等）',
    value_json      MEDIUMTEXT    NULL                   COMMENT '事实值JSON（结构化，保留原始层级）',
    source_path     VARCHAR(512)  DEFAULT NULL           COMMENT '来源路径（仓库相对路径或 git 引用）',
    captured_at     DATETIME      DEFAULT NULL           COMMENT '采集时间',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_fact_type_key (fact_type, fact_key),
    KEY idx_source_path (source_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识Agent结构化事实表';

-- ---------------------------------------------------------------------
-- 会话表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS agent_conversation;
CREATE TABLE agent_conversation (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    user_id         BIGINT        NOT NULL               COMMENT '所属用户ID（网关注入 X-User-Id）',
    title           VARCHAR(128)  DEFAULT NULL           COMMENT '会话标题（默认取首个问题前50字）',
    scope           VARCHAR(128)  DEFAULT NULL           COMMENT '检索范围（逗号分隔 code,doc,sql,git,runtime；空=全部）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识Agent会话表';

-- ---------------------------------------------------------------------
-- 消息表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS agent_message;
CREATE TABLE agent_message (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    conversation_id BIGINT        NOT NULL               COMMENT '所属会话ID',
    role            VARCHAR(16)   NOT NULL               COMMENT '角色：user / assistant',
    content         MEDIUMTEXT    NULL                   COMMENT '消息正文（Markdown）',
    citations_json  MEDIUMTEXT    NULL                   COMMENT '引用块JSON数组（仅 assistant 消息）',
    tokens          INT           NOT NULL DEFAULT 0     COMMENT 'token 统计（prompt + completion）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_conversation_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识Agent消息表';

-- ---------------------------------------------------------------------
-- 回答评价表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS agent_feedback;
CREATE TABLE agent_feedback (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    message_id      BIGINT        NOT NULL               COMMENT '被评价的助手消息ID',
    user_id         BIGINT        DEFAULT NULL           COMMENT '评价用户ID',
    rating          TINYINT       NOT NULL               COMMENT '评价：1 有用 / -1 无用',
    comment         VARCHAR(512)  DEFAULT NULL           COMMENT '评价备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识Agent回答评价表';

-- ---------------------------------------------------------------------
-- 平台写操作确认表（T15：L2 写操作一次性确认令牌）
-- 设计说明：一条记录即一个令牌；PENDING → CONFIRMED 使用条件更新保证只执行一次，
--          拒绝/超时分别落 REJECTED/EXPIRED；参数哈希与结果供审计追溯。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS agent_operation;
CREATE TABLE agent_operation (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    confirmation_id VARCHAR(64)   NOT NULL               COMMENT '确认令牌ID（UUID）',
    user_id         BIGINT        NOT NULL               COMMENT '发起用户ID',
    conversation_id BIGINT        DEFAULT NULL           COMMENT '来源智能体会话ID',
    operation       VARCHAR(64)   NOT NULL               COMMENT '操作名，如 form.definition.create',
    params_json     TEXT          NULL                   COMMENT '操作参数JSON',
    params_hash     VARCHAR(64)   DEFAULT NULL           COMMENT '参数SHA-256哈希（审计比对）',
    permission      VARCHAR(64)   NOT NULL               COMMENT '执行所需权限码，如 sys:ai:ops:form',
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/CONFIRMED/REJECTED/EXPIRED/SUCCESS/FAILED',
    expires_at      DATETIME      NOT NULL               COMMENT '确认有效期',
    confirmed_at    DATETIME      DEFAULT NULL           COMMENT '确认时间',
    result_json     TEXT          NULL                   COMMENT '下游响应JSON',
    error_msg       VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_confirmation_id (confirmation_id),
    KEY idx_user_status (user_id, status),
    KEY idx_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识Agent平台写操作确认表';

-- ---------------------------------------------------------------------
-- 索引元数据表（单行，保留最新一次构建记录）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS agent_index_meta;
CREATE TABLE agent_index_meta (
    id              BIGINT        NOT NULL               COMMENT '主键ID（固定 1，单行表）',
    index_version   VARCHAR(32)   DEFAULT NULL           COMMENT '索引版本（构建批次标识，如 20261021103000）',
    file_count      INT           NOT NULL DEFAULT 0     COMMENT '已索引文件数',
    chunk_count     INT           NOT NULL DEFAULT 0     COMMENT '已索引知识块数',
    last_build_at   DATETIME      DEFAULT NULL           COMMENT '最近一次构建完成时间',
    status          VARCHAR(16)   NOT NULL DEFAULT 'EMPTY' COMMENT '构建状态：READY/BUILDING/FAILED/EMPTY',
    dimension       INT           NOT NULL DEFAULT 0     COMMENT '向量维度（未启用向量为0）',
    build_millis    BIGINT        DEFAULT NULL           COMMENT '最近一次构建耗时（毫秒）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识Agent索引元数据表';

-- ---------------------------------------------------------------------
-- 操作审计日志表（REQ-SEC-006 / Wave 5.47 轨 E：查询元审计）
-- 设计说明：与 whale-audit 同构，append-only（无 update_time / deleted）；
--           id 雪花、create_time 即记录产生时间；由 common AuditLogAspect 异步落库。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（多租户扩展预留）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-agent',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述，如 知识检索',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 agent_fact / agent_index',
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
