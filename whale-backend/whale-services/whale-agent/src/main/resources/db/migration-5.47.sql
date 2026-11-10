-- =====================================================================
-- BlueWhale whale-agent 增量迁移脚本（Wave 5.47 轨 E：查询元审计）
-- =====================================================================
-- 适用场景 : 已按 Wave 5.47 轨 A/B/C 部署、bluewhale_agent 库中已存在业务表的运行库。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已包含同构表）。
-- 说明     : 本脚本幂等（CREATE TABLE IF NOT EXISTS）。
--            新增 audit_log：与 whale-audit/init.sql 同构、append-only（无 update_time / deleted），
--            由 common 模块 AuditLogAspect 异步写入，用于 /agent/chat、/agent/search、/agent/facts
--            的查询元审计（操作类型 QUERY）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS audit_log (
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
) COMMENT='操作审计日志表';
