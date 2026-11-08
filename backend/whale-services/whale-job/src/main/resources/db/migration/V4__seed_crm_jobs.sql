-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）——第三批：快照/生成类（CRM 4 个）
-- =====================================================================
-- 幂等：按 job_code 判重（job_info 无 job_code 唯一键，老库中可能已有手工
--       插入的同名任务），重复执行不会产生重复任务。
-- 说明：四个任务各自带业务开关（whale.crm.pool-recycle-enabled / whale.crm.ar.enabled /
--       whale.crm.contract.enabled / whale.crm.order-approval.reconcile-enabled），
--       开关关闭时任务返回 skipped 而非失败，因此种子一律启用，由配置决定是否真跑。
-- =====================================================================

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000009, 1, 'CRM 公海自动回收', 'CRM', 'whale-crm', 'crm.pool.recycle',
       '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 CrmPoolRecycleJob#recycle（开关 whale.crm.pool-recycle-enabled，关闭时返回 skipped）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.pool.recycle' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000010, 1, 'CRM 应收快照对账', 'CRM', 'whale-crm', 'crm.ar.snapshot',
       '0 30 2 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'FIRE_ONCE', 2, 300, 1, 1,
       '迁移自 CrmArSnapshotJob#sync（当日账龄快照错过需补跑一次；开关 whale.crm.ar.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.ar.snapshot' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000011, 1, 'CRM 合同投影对账', 'CRM', 'whale-crm', 'crm.contract.sync',
       '0 0 3 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 CrmContractSyncJob#reconcile（开关 whale.crm.contract.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.contract.sync' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000012, 1, 'CRM 审批结果对账', 'CRM', 'whale-crm', 'crm.approval.reconcile',
       '0 */2 * * * ?', 'httpJobHandler', '{"timeoutMillis":600000}',
       'DO_NOTHING', 2, 120, 1, 1,
       '迁移自 CrmApprovalReconcileJob#reconcile（回调幂等，失败可重试；开关 reconcile-enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.approval.reconcile' AND deleted = 0);
