-- =====================================================================
-- BlueWhale 微服务平台 - 统一消息服务（whale-message）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_message
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 幂等约定   : CREATE TABLE IF NOT EXISTS；模板种子使用 NOT EXISTS 幂等插入，可重复执行
-- 租户隔离   : tenant_id 由 starter 的 TenantLineInnerInterceptor 自动追加/填充
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 消息模板
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_template (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    template_code    VARCHAR(64)   NOT NULL                COMMENT '模板编码（租户内逻辑唯一）',
    template_name    VARCHAR(128)  NOT NULL                COMMENT '模板名称',
    title_template   VARCHAR(255)  DEFAULT NULL            COMMENT '标题模板，支持 ${key} 占位符',
    content_template TEXT          DEFAULT NULL            COMMENT '内容模板，支持 ${key} 占位符',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    remark           VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_template_code (tenant_id, template_code),
    KEY idx_tenant_status (tenant_id, status)
) COMMENT='消息模板表';

-- ---------------------------------------------------------------------
-- 2. 逻辑消息记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_record (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    biz_type         VARCHAR(32)   DEFAULT NULL            COMMENT '业务类型，如 PM_TASK / CONTRACT_EXPIRE',
    biz_id           VARCHAR(64)   DEFAULT NULL            COMMENT '业务标识',
    template_code    VARCHAR(64)   DEFAULT NULL            COMMENT '模板编码',
    idempotency_key  VARCHAR(96)   DEFAULT NULL            COMMENT '幂等键（租户内唯一；NULL 不参与唯一约束）',
    title            VARCHAR(128)  DEFAULT NULL            COMMENT '消息标题',
    content          TEXT          DEFAULT NULL            COMMENT '消息正文',
    payload_json     TEXT          DEFAULT NULL            COMMENT '模板变量 JSON',
    channels         VARCHAR(128)  DEFAULT NULL            COMMENT '渠道列表（大写逗号分隔，如 INAPP,IM）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '消息状态：PENDING / SENT / DELIVERED / PARTIAL / FAILED / MOCKED / SKIPPED',
    callback_url     VARCHAR(512)  DEFAULT NULL            COMMENT '回执回调地址（业务方内网端点；空表示不回传）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_idempotency (tenant_id, idempotency_key),
    KEY idx_tenant_biz (tenant_id, biz_type, biz_id),
    KEY idx_tenant_create_time (tenant_id, create_time)
) COMMENT='逻辑消息记录表';

-- ---------------------------------------------------------------------
-- 3. 单渠道投递记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_delivery (
    id                 BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    message_id         BIGINT       NOT NULL                COMMENT '消息ID（逻辑关联 msg_record.id）',
    channel            VARCHAR(16)  NOT NULL                COMMENT '渠道：INAPP / EMAIL / SMS / IM',
    recipient          VARCHAR(128) NOT NULL                COMMENT '接收标识：用户ID / 邮箱 / 手机号',
    status             VARCHAR(16)  NOT NULL                COMMENT '投递状态：SENT / DELIVERED / READ / BOUNCED / FAILED / SKIPPED / MOCKED',
    provider_message_id VARCHAR(128) DEFAULT NULL           COMMENT '渠道回执标识（可选）',
    error_message      VARCHAR(512) DEFAULT NULL            COMMENT '失败/跳过原因摘要',
    attempts           INT          NOT NULL DEFAULT 1      COMMENT '尝试次数',
    sent_at            DATETIME     DEFAULT NULL            COMMENT '投递时间',
    delivered_at       DATETIME     DEFAULT NULL            COMMENT '渠道确认送达时间',
    read_at            DATETIME     DEFAULT NULL            COMMENT '接收人阅读时间（仅可回传阅读态的渠道）',
    read_flag          TINYINT      NOT NULL DEFAULT 0      COMMENT '站内信已读标记：0 未读 / 1 已读（非 INAPP 渠道恒为 0）',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_channel_recipient (message_id, channel, recipient),
    KEY idx_tenant_message (tenant_id, message_id),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_tenant_provider (tenant_id, channel, provider_message_id),
    KEY idx_create_time (create_time)
) COMMENT='消息投递记录表';

-- ---------------------------------------------------------------------
-- 3.1 系统公告（标题/正文/业务类型在关联的 msg_record 上）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_announcement (
    id             BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    record_id      BIGINT       NOT NULL               COMMENT '消息ID（关联 msg_record.id，标题/正文/业务类型在此）',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '公告状态：PUBLISHED 已发布 / REVOKED 已撤回',
    publisher_id   BIGINT       DEFAULT NULL           COMMENT '发布人用户ID',
    publish_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_id (record_id),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_publish_time (publish_time)
) COMMENT='系统公告表';

-- ---------------------------------------------------------------------
-- 3.2 送达回执流水（V2）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_receipt (
    id                  BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    receipt_key         VARCHAR(160) NOT NULL                COMMENT '回执幂等键：渠道:回执ID',
    channel             VARCHAR(16)  NOT NULL                COMMENT '渠道：INAPP / EMAIL / SMS / IM',
    provider_message_id VARCHAR(128) DEFAULT NULL            COMMENT '渠道侧消息标识',
    delivery_id         BIGINT       DEFAULT NULL            COMMENT '匹配到的投递记录ID',
    message_id          BIGINT       DEFAULT NULL            COMMENT '匹配到的消息ID',
    receipt_status      VARCHAR(16)  NOT NULL                COMMENT '回执状态：DELIVERED / READ / BOUNCED / UNMATCHED',
    match_status        VARCHAR(16)  NOT NULL DEFAULT 'MATCHED' COMMENT '匹配结果：MATCHED / UNMATCHED',
    receipt_time        DATETIME     DEFAULT NULL            COMMENT '渠道回执时间',
    error_code          VARCHAR(64)  DEFAULT NULL            COMMENT '返信错误码',
    error_message       VARCHAR(512) DEFAULT NULL            COMMENT '返信错误说明',
    raw_payload         TEXT         DEFAULT NULL            COMMENT '渠道原始回执报文',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_receipt_key (tenant_id, receipt_key),
    KEY idx_tenant_delivery (tenant_id, delivery_id),
    KEY idx_tenant_provider_msg (tenant_id, channel, provider_message_id),
    KEY idx_create_time (create_time)
) COMMENT='消息送达回执流水表';

-- ---------------------------------------------------------------------
-- 3.3 回执回调任务（V2，outbox）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_callback_task (
    id            BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    message_id    BIGINT       NOT NULL                COMMENT '消息ID',
    delivery_id   BIGINT       DEFAULT NULL            COMMENT '投递记录ID',
    receipt_id    BIGINT       NOT NULL                COMMENT '触发本回调的回执流水ID',
    callback_url  VARCHAR(512) NOT NULL                COMMENT '回调地址',
    payload_json  TEXT         DEFAULT NULL            COMMENT '回调报文 JSON',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '回调状态：PENDING / DISPATCHING / SENT / FAILED / GAVE_UP',
    dispatch_owner VARCHAR(64) DEFAULT NULL            COMMENT '派发实例标识（抢占成功时写入）',
    dispatch_at   DATETIME     DEFAULT NULL            COMMENT '派发抢占时间（超时回收依据）',
    attempts      INT          NOT NULL DEFAULT 0      COMMENT '已尝试次数',
    next_retry_at DATETIME     DEFAULT NULL            COMMENT '下次重试时间',
    last_error    VARCHAR(512) DEFAULT NULL            COMMENT '最近一次失败原因',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_receipt_delivery (tenant_id, receipt_id, delivery_id),
    KEY idx_status_retry (status, next_retry_at),
    KEY idx_status_dispatch (status, dispatch_at),
    KEY idx_tenant_message (tenant_id, message_id)
) COMMENT='消息回执回调任务表（outbox）';

-- ---------------------------------------------------------------------
-- 4. 内置模板（平台租户共享）
-- ---------------------------------------------------------------------
INSERT INTO msg_template (id, tenant_id, template_code, template_name, title_template, content_template, status, remark)
SELECT 900000000000000001, 1, 'SYSTEM_NOTICE', '系统通知', '系统通知', '${content}', 1, '内置示例模板：标题固定，正文取请求 content 或 payload.content'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM msg_template WHERE id = 900000000000000001 OR (tenant_id = 1 AND template_code = 'SYSTEM_NOTICE'));

-- ---------------------------------------------------------------------
-- 5. 操作审计日志表（whale-spring-boot-starter @AuditLog 切面采集，
--    结构与 bluewhale_auth.audit_log 一致；模板增删改与投递重试均写入）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-message',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 msg_template / msg_record',
    target_id      VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象ID',
    request_params TEXT          NULL                   COMMENT '请求参数（JSON，敏感字段已脱敏）',
    result_body    TEXT          NULL                   COMMENT '返回体（JSON，脱敏 + 截断）',
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
) COMMENT='操作审计日志表';

-- ---------------------------------------------------------------------
-- 6. 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，
--    结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-message',
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
) COMMENT='后端接口日志表';
