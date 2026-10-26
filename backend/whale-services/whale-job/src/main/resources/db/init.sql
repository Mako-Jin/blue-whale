-- =====================================================================
-- BlueWhale 微服务平台 - 任务调度服务（whale-job）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_job
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 说明       : job_log 为执行流水表，冗余 job_name / handler_bean 便于检索；
--              job_id 为逻辑外键；job_dependency 描述任务 DAG 依赖边；
--              job_alert 记录任务失败告警流水
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 定时任务信息表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_info;
CREATE TABLE job_info (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离；调度时绑定对应租户上下文）',
    job_name        VARCHAR(64)   NOT NULL               COMMENT '任务名称',
    job_group       VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组，便于分类管理',
    cron_expression VARCHAR(64)   DEFAULT NULL           COMMENT 'Cron 执行表达式，如 0 0/5 * * * ?',
    handler_bean    VARCHAR(128)  NOT NULL               COMMENT '执行器 Bean 名称（Spring 容器中的处理器）',
    handler_param   VARCHAR(512)  DEFAULT NULL           COMMENT '执行器参数（任务执行时透传）',
    misfire_policy  VARCHAR(16)   NOT NULL DEFAULT 'SMART' COMMENT '错过执行策略：SMART 智能补偿 / FIRE_ONCE 立即执行一次 / DO_NOTHING 放弃错过',
    max_retry       INT           NOT NULL DEFAULT 0     COMMENT '失败重试次数上限：0 表示不重试（建议不超过 5）',
    retry_interval_seconds INT    NOT NULL DEFAULT 60    COMMENT '重试间隔秒数',
    shard_total     INT           NOT NULL DEFAULT 1     COMMENT '分片总数：1 表示不分片（建议不超过 16）',
    status          TINYINT       NOT NULL DEFAULT 0     COMMENT '任务状态：1 运行中 / 0 已停止',
    remark          VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_handler_bean (handler_bean)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务信息表';

-- ---------------------------------------------------------------------
-- 2. 定时任务执行日志表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_log;
CREATE TABLE job_log (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    job_id       BIGINT        NOT NULL               COMMENT '任务ID（逻辑关联 job_info.id）',
    job_name     VARCHAR(64)   NOT NULL               COMMENT '任务名称（冗余，便于日志检索）',
    handler_bean VARCHAR(128)  NOT NULL               COMMENT '执行器 Bean 名称（冗余）',
    run_id       VARCHAR(64)   DEFAULT NULL           COMMENT '执行批次ID：一次根触发与其链式下游共享同一 run_id',
    trigger_type VARCHAR(16)   NOT NULL DEFAULT 'CRON' COMMENT '触发类型：CRON 定时 / MANUAL 手动 / CHAIN 依赖链',
    start_time   DATETIME      NOT NULL               COMMENT '开始执行时间',
    end_time     DATETIME      DEFAULT NULL           COMMENT '执行结束时间（执行中为 NULL）',
    status       TINYINT       NOT NULL DEFAULT 0     COMMENT '执行状态：1 成功 / 0 执行中 / -1 失败',
    retry_count  INT           NOT NULL DEFAULT 0     COMMENT '本次执行已重试次数（首次执行为 0）',
    shard_index  INT           NOT NULL DEFAULT 0     COMMENT '当前分片序号（从 0 开始；不分片为 0）',
    error_msg    TEXT          NULL                   COMMENT '失败原因（异常信息摘要）',
    result       TEXT          NULL                   COMMENT '执行结果（返回数据摘要）',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（与 start_time 同步写入）',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（结束时回写 end_time/status 等字段）',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_job_id (job_id),
    KEY idx_run_id (run_id),
    KEY idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务执行日志表';

-- ---------------------------------------------------------------------
-- 3. 定时任务依赖关系表（DAG 上游/下游边）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_dependency;
CREATE TABLE job_dependency (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    job_id          BIGINT        NOT NULL               COMMENT '下游任务ID（逻辑关联 job_info.id）',
    upstream_job_id BIGINT        NOT NULL               COMMENT '上游任务ID（逻辑关联 job_info.id）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_upstream (job_id, upstream_job_id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_upstream_job (upstream_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务依赖关系表';

-- ---------------------------------------------------------------------
-- 4. 定时任务失败告警表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_alert;
CREATE TABLE job_alert (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    job_id      BIGINT        NOT NULL               COMMENT '任务ID（逻辑关联 job_info.id）',
    job_name    VARCHAR(64)   NOT NULL               COMMENT '任务名称（冗余）',
    log_id      BIGINT        DEFAULT NULL           COMMENT '关联执行日志ID（逻辑关联 job_log.id）',
    run_id      VARCHAR(64)   DEFAULT NULL           COMMENT '执行批次ID（与 job_log.run_id 一致）',
    level       VARCHAR(16)   NOT NULL DEFAULT 'ERROR' COMMENT '告警级别，默认 ERROR',
    channel     VARCHAR(32)   DEFAULT NULL           COMMENT '告警通道：LOG 仅本地日志 / WEBHOOK 外发',
    status      TINYINT       NOT NULL DEFAULT 0     COMMENT '投递状态：1 成功 / -1 失败 / 0 待发',
    message     VARCHAR(512)  DEFAULT NULL           COMMENT '告警摘要',
    error_msg   TEXT          NULL                   COMMENT '失败原因（异常信息摘要）',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_job_id (job_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务失败告警表';

-- ---------------------------------------------------------------------
-- 5. 内置任务种子数据
-- ---------------------------------------------------------------------
-- 每日 03:00 清理超过保留期的执行日志（handler_bean 对应 JobLogCleanupHandler）；
-- 保留天数由配置项 whale.job.log-retention-days 控制（默认 30 天），此处默认启动该任务。
INSERT INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1, '执行日志清理', 'SYSTEM', '0 0 3 * * ?', 'jobLogCleanupHandler', NULL,
        'SMART', 1, '内置任务：逻辑删除超过保留期的 job_log，保留天数见 whale.job.log-retention-days');

-- 每日 04:00 归档导出超过保留期的执行日志：导出 CSV 上传 whale-file 成功后再逻辑删除
-- （handler_bean 对应 JobLogArchiveHandler）；保留天数与单批上限分别由配置项
-- whale.job.log-retention-days（默认 30 天）与 whale.job.archive-batch-size（默认 2000）控制。
INSERT INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (2, '执行日志归档', 'SYSTEM', '0 0 4 * * ?', 'jobLogArchiveHandler', NULL,
        'SMART', 1, '内置任务：导出过期 job_log 至 whale-file 后清理，保留天数见 whale.job.log-retention-days');

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后端接口日志表';