-- =====================================================================
-- BlueWhale whale-job 增量迁移脚本（P3：任务 DAG 依赖编排 / 失败告警通知）
-- =====================================================================
-- 适用场景 : 已按既有版本部署、库中已存在 job_info / job_log 表的运行库
--             （保留存量任务与执行日志）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步本次变更）。
-- 说明     : 本脚本幂等：
--             · 新增列经 information_schema.COLUMNS 存在性守卫 + PREPARE 动态执行，
--               规避 MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS 而重复执行报错；
--             · 新增索引经 information_schema.STATISTICS 守卫；
--             · 新表使用 CREATE TABLE IF NOT EXISTS。
--             · job_log.run_id                    执行批次ID（一次根触发及其链式下游共享）
--             · job_log.trigger_type              触发类型（CRON / MANUAL / CHAIN）
--             · job_log.idx_run_id                按执行批次检索索引
--             · job_dependency                    任务依赖边（DAG）
--             · job_alert                         任务失败告警流水
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 定时任务执行日志表：执行批次ID
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE job_log ADD COLUMN run_id VARCHAR(64) DEFAULT NULL COMMENT ''执行批次ID：一次根触发与其链式下游共享同一 run_id'' AFTER handler_bean',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND COLUMN_NAME = 'run_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 定时任务执行日志表：触发类型
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE job_log ADD COLUMN trigger_type VARCHAR(16) NOT NULL DEFAULT ''CRON'' COMMENT ''触发类型：CRON 定时 / MANUAL 手动 / CHAIN 依赖链'' AFTER run_id',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND COLUMN_NAME = 'trigger_type');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 定时任务执行日志表：执行批次ID索引
-- ---------------------------------------------------------------------
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE job_log ADD KEY idx_run_id (run_id)',
    'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_log' AND INDEX_NAME = 'idx_run_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 定时任务依赖关系表（DAG 上游/下游边）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS job_dependency (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    job_id          BIGINT        NOT NULL               COMMENT '下游任务ID（逻辑关联 job_info.id）',
    upstream_job_id BIGINT        NOT NULL               COMMENT '上游任务ID（逻辑关联 job_info.id）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_upstream (job_id, upstream_job_id),
    KEY idx_upstream_job (upstream_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务依赖关系表';

-- ---------------------------------------------------------------------
-- 5. 定时任务失败告警表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS job_alert (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
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
    KEY idx_job_id (job_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务失败告警表';
