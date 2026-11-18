-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）——第四批：PDM 变更生效 + Archive 2 个
-- =====================================================================
-- 幂等：按 job_code 判重（job_info 无 job_code 唯一键，老库中可能已有手工
--       插入的同名任务），重复执行不会重复产生任务。
-- 说明：三个任务都保留各自业务开关（pdm.change.effective-job-enabled /
--       whale.archive.sync.enabled / whale.archive.lock-reconcile.enabled），
--       开关关闭时返回 skipped 而非失败，因此种子一律启用、由配置决定是否真跑。
-- =====================================================================

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000013, 1, 'PDM 变更到期生效', 'PDM', 'whale-pdm', 'pdm.change.effective',
       '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 PdmChgEffectiveJob#scheduledRun（库内锁 pdm_chg_job_lock 仍在，开关 effective-job-enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'pdm.change.effective' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000014, 1, '档案归档同步', 'ARCHIVE', 'whale-archive', 'archive.sync',
       '0 */5 * * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 ArchiveSyncScheduler#sync（逐租户执行，开关 whale.archive.sync.enabled 默认关闭）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'archive.sync' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000015, 1, '档案文件锁定补扫', 'ARCHIVE', 'whale-archive', 'archive.file-lock-reconcile',
       '0 30 3 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 ArchiveFileLockReconcileScheduler#reconcile（逐租户游标补锁，开关 lock-reconcile.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'archive.file-lock-reconcile' AND deleted = 0);
