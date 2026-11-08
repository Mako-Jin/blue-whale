-- =====================================================================
-- 补建操作审计表 audit_log（幂等）
-- =====================================================================
-- 背景：starter 的 AuditLogAspect 会在 job 服务上装配（管理端改 cron、
--       启停任务等操作带 @AuditLog 注解），但 job 库一直没有这张表，
--       每次操作都抛 BadSqlGrammarException —— 审计没落库，日志里全是堆栈。
-- 结构：与 auth / crm 等库的 audit_log 保持一致（同一实体 AuditLogRecord）。
--       append-only：无 update_time、无 deleted。
-- =====================================================================

CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)  DEFAULT NULL             COMMENT '链路追踪ID',
    user_id        BIGINT       DEFAULT NULL             COMMENT '操作用户ID（匿名为 NULL）',
    tenant_id      BIGINT       DEFAULT NULL             COMMENT '租户ID',
    username       VARCHAR(64)  DEFAULT NULL             COMMENT '操作用户名',
    module         VARCHAR(64)  DEFAULT NULL             COMMENT '业务模块',
    operation      VARCHAR(128) DEFAULT NULL             COMMENT '操作描述',
    operate_type   VARCHAR(32)  DEFAULT NULL             COMMENT '操作类型',
    method         VARCHAR(255) DEFAULT NULL             COMMENT '请求方法（全限定类名#方法名）',
    request_uri    VARCHAR(255) DEFAULT NULL             COMMENT '请求URI',
    http_method    VARCHAR(16)  DEFAULT NULL             COMMENT 'HTTP 方法',
    target_type    VARCHAR(64)  DEFAULT NULL             COMMENT '操作对象类型',
    target_id      VARCHAR(64)  DEFAULT NULL             COMMENT '操作对象ID',
    request_params TEXT         DEFAULT NULL             COMMENT '请求参数（JSON，已脱敏）',
    result_body    TEXT         DEFAULT NULL             COMMENT '返回体（JSON，已脱敏截断）',
    response_code  INT          DEFAULT NULL             COMMENT '响应状态码',
    biz_status     TINYINT      NOT NULL DEFAULT 1       COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512) DEFAULT NULL             COMMENT '失败原因',
    ip             VARCHAR(64)  DEFAULT NULL             COMMENT '操作IP',
    user_agent     VARCHAR(512) DEFAULT NULL             COMMENT '用户代理',
    cost_ms        BIGINT       DEFAULT NULL             COMMENT '接口耗时（毫秒）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_audit_log_trace (trace_id),
    KEY idx_audit_log_user (user_id),
    KEY idx_audit_log_tenant (tenant_id),
    KEY idx_audit_log_module (module),
    KEY idx_audit_log_type (operate_type),
    KEY idx_audit_log_status (biz_status),
    KEY idx_audit_log_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '操作审计日志（append-only）';
