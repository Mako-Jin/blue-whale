-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）——第八批：whale-file 定时任务
-- =====================================================================
-- 背景：whale-file 的回收站清理、存量版本清理、孤儿对账原为进程内 @Scheduled，
--       产生于 2026-11-08 迁移收口之后，未进入当时的 27 项清单。本批统一迁入调度中心。
-- 幂等：按 job_code 判重，重复执行不会重复产生任务。
-- 说明：三个方法内仍保留业务开关（关闭时返回 skipped 摘要），因此种子默认启用，
--       由方法摘要区分「未开启」与「无数据」。
-- =====================================================================

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000028, 1, 'File 回收站到期清理', 'FILE', 'whale-file', 'file.recycle-cleanup',
       '0 30 3 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 RecyclePurgeTask#purgeExpired（对象删除幂等，开关 whale.file.recycle.purge-enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'file.recycle-cleanup' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000029, 1, 'File 存量历史版本清理', 'FILE', 'whale-file', 'file.version-cleanup',
       '0 30 4 * * ?', 'httpJobHandler', '{"timeoutMillis":3600000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 VersionCleanupTask#cleanup（按版本行物理删除，开关 whale.file.version.cleanup-enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'file.version-cleanup' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000030, 1, 'File 孤儿对象对账', 'FILE', 'whale-file', 'file.orphan-reconcile',
       '0 0 4 * * ?', 'httpJobHandler', '{"timeoutMillis":7200000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 OrphanReconcileTask#reconcile（默认只报告，开关 whale.file.orphan.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'file.orphan-reconcile' AND deleted = 0);
