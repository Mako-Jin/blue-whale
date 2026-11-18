-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）
-- =====================================================================
-- 背景：业务侧 @Scheduled 迁入调度中心后，每个任务都需要一条 job_info：
--       executor_app 填 Nacos 服务名、job_code 对应 @ScheduledJob.code()，
--       地址不再落库（由 job_executor 注册表 + Nacos 交集解析）。
-- 幂等：按 job_code 判重（job_info 无 job_code 唯一键，老库中可能已有手工
--       插入的同名任务），重复执行不会产生重复任务。
-- 批次：2026-11-08 第一批（CRM 试点 2 个）+ 第二批（清理类 6 个）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 第一批：CRM 试点（此前手工插入，这里补齐以便新库可复现）
-- ---------------------------------------------------------------------
INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000001, 1, 'CRM 经营快照', 'CRM', 'whale-crm', 'crm.analytics.snapshot',
       '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":600000}',
       'DO_NOTHING', 2, 300, 1, 1, '迁移自 AnalyticsSnapshotJob#generateSnapshots'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.analytics.snapshot' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000002, 1, 'CRM 行动项生成', 'CRM', 'whale-crm', 'crm.analytics.action-plan',
       '0 30 2 * * ?', 'httpJobHandler', '{"timeoutMillis":600000}',
       'DO_NOTHING', 2, 300, 1, 1, '迁移自 AnalyticsSnapshotJob#generateActionItems'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.analytics.action-plan' AND deleted = 0);

-- ---------------------------------------------------------------------
-- 第二批：清理类（只读/可重入，重复执行无副作用，重试给 1 次）
-- ---------------------------------------------------------------------
INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000003, 1, '文档回收站到期清理', 'DOC', 'whale-doc', 'doc.recycle.cleanup',
       '0 30 3 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1, '迁移自 DocRecycleCleanupJob#cleanup'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'doc.recycle.cleanup' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000004, 1, '文档孤儿附件清理', 'DOC', 'whale-doc', 'doc.orphan-attachment.cleanup',
       '0 30 4 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1, '迁移自 DocOrphanAttachmentCleanupJob#cleanup'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'doc.orphan-attachment.cleanup' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000005, 1, '审计日志保留期清理', 'AUDIT', 'whale-audit', 'audit.log.retention',
       '0 0 3 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1, '迁移自 LogRetentionScheduler#cleanExpiredLogs（清理/归档开关仍由配置决定）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'audit.log.retention' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000006, 1, '音乐播放历史清理', 'MUSIC', 'whale-music', 'music.play-history.cleanup',
       '0 0 3 * * ?', 'httpJobHandler', '{"timeoutMillis":900000}',
       'DO_NOTHING', 0, 60, 1, 1, '迁移自 PlayHistoryCleanupScheduler#purgeExpiredHistory'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'music.play-history.cleanup' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000007, 1, '电影授权到期下线', 'VIDEO', 'whale-video', 'video.license-expiry.offline',
       '0 10 3 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1, '迁移自 MovieLicenseExpiryScheduler#offlineExpired'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'video.license-expiry.offline' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000008, 1, '观看历史保留期清理', 'VIDEO', 'whale-video', 'video.watch-history.cleanup',
       '0 40 4 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1, '迁移自 WatchHistoryCleanupScheduler#cleanup'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'video.watch-history.cleanup' AND deleted = 0);
