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
--              job_id 为逻辑外键
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 定时任务信息表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_info;
CREATE TABLE job_info (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    job_name        VARCHAR(64)   NOT NULL               COMMENT '任务名称',
    job_group       VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组，便于分类管理',
    cron_expression VARCHAR(64)   DEFAULT NULL           COMMENT 'Cron 执行表达式，如 0 0/5 * * * ?',
    handler_bean    VARCHAR(128)  NOT NULL               COMMENT '执行器 Bean 名称（Spring 容器中的处理器）',
    handler_param   VARCHAR(512)  DEFAULT NULL           COMMENT '执行器参数（任务执行时透传）',
    misfire_policy  VARCHAR(16)   NOT NULL DEFAULT 'SMART' COMMENT '错过执行策略：SMART 智能补偿 / FIRE_ONCE 立即执行一次 / DO_NOTHING 放弃错过',
    status          TINYINT       NOT NULL DEFAULT 0     COMMENT '任务状态：1 运行中 / 0 已停止',
    remark          VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_handler_bean (handler_bean)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务信息表';

-- ---------------------------------------------------------------------
-- 2. 定时任务执行日志表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_log;
CREATE TABLE job_log (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    job_id       BIGINT        NOT NULL               COMMENT '任务ID（逻辑关联 job_info.id）',
    job_name     VARCHAR(64)   NOT NULL               COMMENT '任务名称（冗余，便于日志检索）',
    handler_bean VARCHAR(128)  NOT NULL               COMMENT '执行器 Bean 名称（冗余）',
    start_time   DATETIME      NOT NULL               COMMENT '开始执行时间',
    end_time     DATETIME      DEFAULT NULL           COMMENT '执行结束时间（执行中为 NULL）',
    status       TINYINT       NOT NULL DEFAULT 0     COMMENT '执行状态：1 成功 / 0 执行中 / -1 失败',
    error_msg    TEXT          NULL                   COMMENT '失败原因（异常信息摘要）',
    result       TEXT          NULL                   COMMENT '执行结果（返回数据摘要）',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（与 start_time 同步写入）',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（结束时回写 end_time/status 等字段）',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_job_id (job_id),
    KEY idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务执行日志表';

-- ---------------------------------------------------------------------
-- 3. 内置任务种子数据
-- ---------------------------------------------------------------------
-- 每日 03:00 清理超过保留期的执行日志（handler_bean 对应 JobLogCleanupHandler）；
-- 保留天数由配置项 whale.job.log-retention-days 控制（默认 30 天），此处默认启动该任务。
INSERT INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1, '执行日志清理', 'SYSTEM', '0 0 3 * * ?', 'jobLogCleanupHandler', NULL,
        'SMART', 1, '内置任务：逻辑删除超过保留期的 job_log，保留天数见 whale.job.log-retention-days');
