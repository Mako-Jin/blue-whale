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
-- =====================================================================

-- ---------------------------------------------------------------------
-- 操作审计日志表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述，如 创建用户',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    request_params TEXT          NULL                   COMMENT '请求参数（JSON 格式，敏感字段须脱敏后写入）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '操作IP地址（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即操作发生时间）',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_user_id (user_id),
    KEY idx_module (module),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作审计日志表';
