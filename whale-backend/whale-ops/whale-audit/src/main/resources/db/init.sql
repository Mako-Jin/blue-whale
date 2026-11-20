-- =====================================================================
-- BlueWhale 微服务平台 - 审计服务（whale-audit）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_audit
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time
-- 设计说明   : 审计日志为追加型（append-only）数据，写入后不允许修改，
--              因此不设 update_time；日志按保留策略整批归档/清理，
--              而非逐条逻辑删除，逻辑删除位无业务意义且增加存储与索引开销，
--              因此不设 deleted。create_time 即操作发生时间，也是主要查询维度。
-- 字段补全   : 按 docs/design/audit-log.md §4.2 补充 tenant_id / operate_type / http_method /
--              target_type / target_id / biz_status / error_msg 共 7 个字段及 3 个索引。
--              该表尚未在任何环境部署（whale-audit 无读写代码），故将设计中的 ALTER 语句
--              直接折叠进 CREATE TABLE，保持初始化脚本幂等可重跑；项目未引入 Flyway/Liquibase，
--              不额外新建 migration 文件。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 操作审计日志表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（多租户扩展预留）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述，如 创建用户',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 sys_user / sys_role',
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
) COMMENT='操作审计日志表';

-- ---------------------------------------------------------------------
-- 审计日志追加写守卫（等保 2.0：审计记录应受到保护，避免被删除、修改或覆盖）
-- 设计说明：audit_log 为合规证据表，只允许 INSERT + SELECT。应用层由
--           scripts/security/check-audit-append-only.py 拦住改写语句，数据库层
--           由下面两个触发器拦住手工 SQL 与 DBA 客户端；归档任务只导出不删除，
--           不受影响。幂等：先 DROP TRIGGER IF EXISTS 再建。
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_audit_log_no_update;
CREATE TRIGGER trg_audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_log is append-only, UPDATE is forbidden';

DROP TRIGGER IF EXISTS trg_audit_log_no_delete;
CREATE TRIGGER trg_audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_log is append-only, DELETE is forbidden';

-- ---------------------------------------------------------------------
-- 审计日志归档水位表（AUTH-09，Wave 5.48）
-- 设计说明：audit_log 为 append-only，归档只导出不删除；以单行水位记录
--           上一轮成功归档批次的最后一条 create_time + id，避免每轮 cron
--           重复导出同一批过期记录。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_archive_watermark;
CREATE TABLE audit_archive_watermark (
    id               BIGINT   NOT NULL               COMMENT '固定单行主键，恒为 1',
    last_create_time DATETIME DEFAULT NULL           COMMENT '已归档批次的最后一条 audit_log.create_time',
    last_id          BIGINT   DEFAULT NULL           COMMENT '已归档批次的最后一条 audit_log.id（同一 create_time 内去重）',
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '水位更新时间',
    PRIMARY KEY (id)
) COMMENT='审计日志归档水位表';

-- ---------------------------------------------------------------------
-- 多通道观测日志表（REQ-SEC-006，Wave 5.10）
-- 设计说明：与 audit_log 一致为 append-only（无 update_time / deleted），
--           id 雪花、create_time 即记录产生时间；4 张独立表，不污染 audit_log 语义。
--           字段与索引以 docs/specs/2026-10-18-wave5.10-multichannel-log/spec.md §3.1 为准。
-- ---------------------------------------------------------------------

-- 后端接口日志表（ApiLogFilter 采集）
DROP TABLE IF EXISTS api_log;
CREATE TABLE api_log (
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
) COMMENT='后端接口日志表';

-- 后端出站三方调用日志表（OutboundLogInterceptor 采集）
DROP TABLE IF EXISTS outbound_log;
CREATE TABLE outbound_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（出站请求头透传）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '发起调用的用户ID',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '发起调用的用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（请求上下文）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    target_host    VARCHAR(255)  DEFAULT NULL           COMMENT '目标主机（host:port）',
    target_uri     VARCHAR(512)  DEFAULT NULL           COMMENT '目标URI（含路径，query 已脱敏）',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法',
    request_params TEXT          NULL                   COMMENT '出站请求体（JSON，敏感字段已脱敏）',
    response_body  TEXT          NULL                   COMMENT '出站响应体（JSON，敏感字段已脱敏，超限截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码（HTTP 状态）',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败（超时/连接异常为 0）',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '调用耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_target_host (target_host),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_biz_status (biz_status)
) COMMENT='后端出站三方调用日志表';

-- 前端开发者日志表（POST /audit/log/frontend/batch 落库）
DROP TABLE IF EXISTS frontend_log;
CREATE TABLE frontend_log (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id    VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID',
    user_id     BIGINT        DEFAULT NULL           COMMENT '上报用户ID（token 存在时由网关回填）',
    tenant_id   BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    client_id   VARCHAR(64)   DEFAULT NULL           COMMENT '前端客户端标识（一次会话唯一）',
    page_url    VARCHAR(1024) DEFAULT NULL           COMMENT '页面URL（query 已掩码）',
    log_level   VARCHAR(16)   DEFAULT NULL           COMMENT '日志级别：DEBUG/INFO/WARN/ERROR',
    message     TEXT          NULL                   COMMENT '日志内容（超限截断，敏感键掩码）',
    stack       TEXT          NULL                   COMMENT '错误堆栈（ERROR 时）',
    browser     VARCHAR(255)  DEFAULT NULL           COMMENT '浏览器/UA 摘要',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（前端产生时间约为上报时间）',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_client_id (client_id),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_log_level (log_level)
) COMMENT='前端开发者日志表';

-- 前端用户行为埋点日志表（POST /audit/log/behavior/batch 落库）
DROP TABLE IF EXISTS behavior_log;
CREATE TABLE behavior_log (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id    VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID',
    user_id     BIGINT        DEFAULT NULL           COMMENT '上报用户ID（token 存在时由网关回填）',
    tenant_id   BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    client_id   VARCHAR(64)   DEFAULT NULL           COMMENT '前端客户端标识（一次会话唯一）',
    event_type  VARCHAR(32)   DEFAULT NULL           COMMENT '事件类型：PAGE_VIEW/CLICK/SUBMIT/CUSTOM',
    event_name  VARCHAR(128)  DEFAULT NULL           COMMENT '事件名称，如 用户管理页访问',
    page_url    VARCHAR(1024) DEFAULT NULL           COMMENT '页面URL（query 已掩码）',
    target      VARCHAR(255)  DEFAULT NULL           COMMENT '触发目标（元素标识，如按钮 name/id）',
    payload     TEXT          NULL                   COMMENT '事件载荷（JSON，敏感字段已掩码）',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_client_id (client_id),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_event_type (event_type)
) COMMENT='前端用户行为埋点日志表';

-- ---------------------------------------------------------------------
-- 审计 MQ 消息去重表（AUTH-10，RabbitMQ 汇聚幂等）
-- 设计说明：RabbitMQ 为「至少一次」投递语义，消费端以生产者生成的 messageId
--           为主键去重；去重行与 audit_log 同事务写入，主键冲突说明消息已消费，
--           直接跳过，保证 audit_log 不被重复追加。超过保留期可随审计日志一并清理。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_mq_message;
CREATE TABLE audit_mq_message (
    message_id  VARCHAR(64) NOT NULL               COMMENT 'MQ 消息ID（生产者生成的 32 位 UUID，幂等去重键）',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次消费时间',
    PRIMARY KEY (message_id),
    KEY idx_create_time (create_time)
) COMMENT='审计 MQ 消息去重表';
