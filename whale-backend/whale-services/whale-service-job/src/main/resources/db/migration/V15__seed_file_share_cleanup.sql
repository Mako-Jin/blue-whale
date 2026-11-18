-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）——批次 32：whale-file 到期分享清理
-- =====================================================================
-- 背景：分享链接有有效期与下载次数上限，失效后记录永久留在 file_share；新增定时任务
--       物理删除到期超过保留期的分享，避免失效链接无限累积拖慢分页查询。
-- 幂等：按 job_code 判重，重复执行不会重复产生任务。
-- 说明：方法内保留业务开关（关闭时返回 skipped 摘要），因此种子默认启用，
--       由方法摘要区分「未开启」与「无数据」。
-- =====================================================================

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000031, 1, 'File 到期分享清理', 'FILE', 'whale-file', 'file.share-cleanup',
       '0 10 4 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 ShareCleanupTask#purgeExpired（物理删除到期分享，开关 whale.file.share.purge-enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'file.share-cleanup' AND deleted = 0);
