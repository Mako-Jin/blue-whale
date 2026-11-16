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
    executor_app    VARCHAR(64)   DEFAULT NULL           COMMENT '执行器应用名（Nacos 服务名），HTTP 型任务据此寻址',
    job_code        VARCHAR(128)  DEFAULT NULL           COMMENT '任务编码（对应 @ScheduledJob.code），为空时按执行器参数直连',
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
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_handler_bean (handler_bean)
) COMMENT='定时任务信息表';

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
    executor_address VARCHAR(128) DEFAULT NULL        COMMENT '实际派发到的执行器地址（host:port），回捞执行日志用',
    error_msg    TEXT          NULL                   COMMENT '失败原因（异常信息摘要）',
    result       TEXT          NULL                   COMMENT '执行结果（返回数据摘要）',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（与 start_time 同步写入）',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间（结束时回写 end_time/status 等字段）',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_job_id (job_id),
    KEY idx_run_id (run_id),
    KEY idx_start_time (start_time)
) COMMENT='定时任务执行日志表';

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
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_upstream (job_id, upstream_job_id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_upstream_job (upstream_job_id)
) COMMENT='定时任务依赖关系表';

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
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_job_id (job_id),
    KEY idx_create_time (create_time)
) COMMENT='定时任务失败告警表';

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

-- 已迁移业务任务（与 db/migration/V3__seed_migrated_jobs.sql、V4__seed_crm_jobs.sql、
-- V5__seed_pdm_archive_jobs.sql、V6__seed_oa_jobs.sql、V7__seed_lms_party_jobs.sql、
-- V8__seed_push_jobs.sql 同源）：
-- executor_app 为 Nacos 服务名，job_code 对应业务侧 @ScheduledJob.code()，
-- 地址不落库（由 job_executor 注册表 + Nacos 实例交集解析）。
INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds, shard_total, status, remark)
VALUES
(9100000000000000001, 1, 'CRM 经营快照', 'CRM', 'whale-crm', 'crm.analytics.snapshot',
 '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":600000}', 'DO_NOTHING', 2, 300, 1, 1,
 '迁移自 AnalyticsSnapshotJob#generateSnapshots'),
(9100000000000000002, 1, 'CRM 行动项生成', 'CRM', 'whale-crm', 'crm.analytics.action-plan',
 '0 30 2 * * ?', 'httpJobHandler', '{"timeoutMillis":600000}', 'DO_NOTHING', 2, 300, 1, 1,
 '迁移自 AnalyticsSnapshotJob#generateActionItems'),
(9100000000000000003, 1, '文档回收站到期清理', 'DOC', 'whale-doc', 'doc.recycle.cleanup',
 '0 30 3 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 DocRecycleCleanupJob#cleanup'),
(9100000000000000004, 1, '文档孤儿附件清理', 'DOC', 'whale-doc', 'doc.orphan-attachment.cleanup',
 '0 30 4 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 DocOrphanAttachmentCleanupJob#cleanup'),
(9100000000000000005, 1, '审计日志保留期清理', 'AUDIT', 'whale-audit', 'audit.log.retention',
 '0 0 3 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 LogRetentionScheduler#cleanExpiredLogs（清理/归档开关仍由配置决定）'),
(9100000000000000006, 1, '音乐播放历史清理', 'MUSIC', 'whale-music', 'music.play-history.cleanup',
 '0 0 3 * * ?', 'httpJobHandler', '{"timeoutMillis":900000}', 'DO_NOTHING', 0, 60, 1, 1,
 '迁移自 PlayHistoryCleanupScheduler#purgeExpiredHistory'),
(9100000000000000007, 1, '电影授权到期下线', 'VIDEO', 'whale-video', 'video.license-expiry.offline',
 '0 10 3 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 MovieLicenseExpiryScheduler#offlineExpired'),
(9100000000000000008, 1, '观看历史保留期清理', 'VIDEO', 'whale-video', 'video.watch-history.cleanup',
 '0 40 4 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 WatchHistoryCleanupScheduler#cleanup'),
(9100000000000000009, 1, 'CRM 公海自动回收', 'CRM', 'whale-crm', 'crm.pool.recycle',
 '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 CrmPoolRecycleJob#recycle（开关 whale.crm.pool-recycle-enabled，关闭时返回 skipped）'),
(9100000000000000010, 1, 'CRM 应收快照对账', 'CRM', 'whale-crm', 'crm.ar.snapshot',
 '0 30 2 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'FIRE_ONCE', 2, 300, 1, 1,
 '迁移自 CrmArSnapshotJob#sync（当日账龄快照错过需补跑一次；开关 whale.crm.ar.enabled）'),
(9100000000000000011, 1, 'CRM 合同投影对账', 'CRM', 'whale-crm', 'crm.contract.sync',
 '0 0 3 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 CrmContractSyncJob#reconcile（开关 whale.crm.contract.enabled）'),
(9100000000000000012, 1, 'CRM 审批结果对账', 'CRM', 'whale-crm', 'crm.approval.reconcile',
 '0 */2 * * * ?', 'httpJobHandler', '{"timeoutMillis":600000}', 'DO_NOTHING', 2, 120, 1, 1,
 '迁移自 CrmApprovalReconcileJob#reconcile（回调幂等，失败可重试；开关 reconcile-enabled）'),
(9100000000000000013, 1, 'PDM 变更到期生效', 'PDM', 'whale-pdm', 'pdm.change.effective',
 '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 PdmChgEffectiveJob#scheduledRun（库内锁 pdm_chg_job_lock 仍在，开关 effective-job-enabled）'),
(9100000000000000014, 1, '档案归档同步', 'ARCHIVE', 'whale-archive', 'archive.sync',
 '0 */5 * * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 ArchiveSyncScheduler#sync（逐租户执行，开关 whale.archive.sync.enabled 默认关闭）'),
(9100000000000000015, 1, '档案文件锁定补扫', 'ARCHIVE', 'whale-archive', 'archive.file-lock-reconcile',
 '0 30 3 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 ArchiveFileLockReconcileScheduler#reconcile（逐租户游标补锁，开关 lock-reconcile.enabled）'),
(9100000000000000016, 1, 'OA 审批自动催办', 'OA', 'whale-oa', 'oa.approval.auto-urge',
 '0 0 */2 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 ApprovalMaintenanceJob#autoUrge（消息服务按天去重，开关 auto-urge.enabled）'),
(9100000000000000017, 1, 'OA 审批状态对账', 'OA', 'whale-oa', 'oa.approval.reconcile',
 '0 */30 * * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 2, 300, 1, 1,
 '迁移自 ApprovalMaintenanceJob#reconcile（回写幂等，失败可重试；开关 reconcile.enabled）'),
(9100000000000000018, 1, 'OA 出差报告未交提醒', 'OA', 'whale-oa', 'oa.trip.report-reminder',
 '0 0 9 * * *', 'httpJobHandler', '{"timeoutMillis":900000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 TripReportReminderJob#remind（开关 report-reminder.enabled）'),
(9100000000000000019, 1, 'LMS 未完成必修催办', 'LMS', 'whale-lms', 'lms.urge.daily',
 '0 0 9 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'FIRE_ONCE', 1, 300, 1, 1,
 '迁移自 LmsUrgeJob#urgeDaily（按日幂等，重复触发不重复发；cron 可配 whale.lms.urge.cron）'),
(9100000000000000020, 1, 'Party 党务/工会通知扫描', 'PARTY', 'whale-party', 'party.notice.scan',
 '0 0 9 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'FIRE_ONCE', 1, 300, 1, 1,
 '迁移自 PartyNoticeJob#scan（按场景去重，重复扫描当日回 0；cron 可配 whale.party.notice-cron）'),
(9100000000000000021, 1, 'CRM 商务订单下发 ERP', 'CRM', 'whale-crm', 'crm.order.push',
 '0 * * * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 2, 60, 1, 1,
 '迁移自 CrmOrderSyncJob#pushPending（outbox 幂等，开关 whale.crm.erp.enabled）'),
(9100000000000000022, 1, 'CRM 商务订单履约对账', 'CRM', 'whale-crm', 'crm.order.reconcile',
 '0 */10 * * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 2, 60, 1, 1,
 '迁移自 CrmOrderSyncJob#reconcile（只读回读 ERP 状态，开关 whale.crm.erp.enabled）'),
(9100000000000000023, 1, 'CRM 退换货下发 ERP', 'CRM', 'whale-crm', 'crm.return.push',
 '0 */5 * * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 2, 60, 1, 1,
 '迁移自 CrmReturnSyncJob#pushPending（outbox 幂等，开关 whale.crm.erp.enabled）'),
(9100000000000000024, 1, 'CRM 退换货履约对账', 'CRM', 'whale-crm', 'crm.return.reconcile',
 '0 0/30 * * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 2, 60, 1, 1,
 '迁移自 CrmReturnSyncJob#reconcile（只读对账，开关 whale.crm.erp.enabled）'),
(9100000000000000025, 1, 'OA 采购申请推送 ERP 订单', 'OA', 'whale-oa', 'oa.purchase.push',
 '0 */10 * * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 2, 60, 1, 1,
 '迁移自 PurchaseOrderPushJob#push（按申请单号幂等，开关 whale.oa.purchase.push.enabled）'),
(9100000000000000026, 1, 'OA 报销单推送 ERP 应付', 'OA', 'whale-oa', 'oa.expense.payable-push',
 '0 */10 * * * *', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 2, 60, 1, 1,
 '迁移自 ExpensePayablePushJob#push（单条失败置 FAILED 继续后续，开关 whale.oa.expense.push.enabled）'),
(9100000000000000027, 1, 'Auth 口令到期提醒', 'AUTH', 'whale-service-auth', 'auth.password.expiry-notice',
 '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'FIRE_ONCE', 1, 300, 1, 1,
 '迁移自 PasswordExpiryNoticeJob#notifyExpiringPasswords（按日幂等，重复触发不重复提醒；cron 可配 whale.auth.password.notice-cron）'),
(9100000000000000028, 1, 'File 回收站到期清理', 'FILE', 'whale-file', 'file.recycle-cleanup',
 '0 30 3 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 RecyclePurgeTask#purgeExpired（对象删除幂等，开关 whale.file.recycle.purge-enabled）'),
(9100000000000000029, 1, 'File 存量历史版本清理', 'FILE', 'whale-file', 'file.version-cleanup',
 '0 30 4 * * ?', 'httpJobHandler', '{"timeoutMillis":3600000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 VersionCleanupTask#cleanup（按版本行物理删除，开关 whale.file.version.cleanup-enabled）'),
(9100000000000000030, 1, 'File 孤儿对象对账', 'FILE', 'whale-file', 'file.orphan-reconcile',
 '0 0 4 * * ?', 'httpJobHandler', '{"timeoutMillis":7200000}', 'DO_NOTHING', 1, 300, 1, 1,
 '迁移自 OrphanReconcileTask#reconcile（默认只报告，开关 whale.file.orphan.enabled）');

-- ---------------------------------------------------------------------
-- 5. 执行器注册表（对齐 xxl-job executor：谁在线、谁支持哪些任务）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_executor;
CREATE TABLE job_executor (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID',
    app_name    VARCHAR(64)   NOT NULL               COMMENT '执行器应用名（Nacos 服务名），如 whale-crm',
    address     VARCHAR(128)  NOT NULL               COMMENT '实例地址 host:port',
    job_codes   VARCHAR(2048) DEFAULT NULL           COMMENT '该实例支持的任务编码，逗号分隔',
    last_beat   DATETIME      NOT NULL               COMMENT '最近心跳时间（超时未心跳视为离线）',
    status      TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 在线 / 0 已注销',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_app_address (tenant_id, app_name, address),
    KEY idx_tenant_app (tenant_id, app_name),
    KEY idx_last_beat (last_beat)
) COMMENT='执行器注册表';

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
) COMMENT='后端接口日志表';

-- ---------------------------------------------------------------------
-- 7. 操作审计日志（append-only：无 update_time、无 deleted）
--    对齐 starter 的 AuditLogRecord；缺表会让管理端操作审计全部落空
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
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
) COMMENT='操作审计日志（append-only）';
