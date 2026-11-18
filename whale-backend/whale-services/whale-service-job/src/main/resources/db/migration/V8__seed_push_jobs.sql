-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）——第七批：推送/对账 6 个（最后一批）
-- =====================================================================
-- 幂等：按 job_code 判重（job_info 无 job_code 唯一键，老库中可能已有手工
--       插入的同名任务），重复执行不会重复产生任务。
-- 说明：这些都是「向 ERP 推单 / 回读 ERP 状态」的任务：
--       * 推送按 outbox + 外部单号幂等，失败可重试，故 max_retry=2；
--       * 对账只读，重复执行无副作用，misfire DO_NOTHING；
--       * 业务开关关闭时任务返回 skipped（不是失败），所以种子一律启用。
-- =====================================================================

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000021, 1, 'CRM 商务订单下发 ERP', 'CRM', 'whale-crm', 'crm.order.push',
       '0 * * * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 2, 60, 1, 1,
       '迁移自 CrmOrderSyncJob#pushPending（outbox 幂等，开关 whale.crm.erp.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.order.push' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000022, 1, 'CRM 商务订单履约对账', 'CRM', 'whale-crm', 'crm.order.reconcile',
       '0 */10 * * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 2, 60, 1, 1,
       '迁移自 CrmOrderSyncJob#reconcile（只读回读 ERP 状态，开关 whale.crm.erp.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.order.reconcile' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000023, 1, 'CRM 退换货下发 ERP', 'CRM', 'whale-crm', 'crm.return.push',
       '0 */5 * * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 2, 60, 1, 1,
       '迁移自 CrmReturnSyncJob#pushPending（outbox 幂等，开关 whale.crm.erp.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.return.push' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000024, 1, 'CRM 退换货履约对账', 'CRM', 'whale-crm', 'crm.return.reconcile',
       '0 0/30 * * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 2, 60, 1, 1,
       '迁移自 CrmReturnSyncJob#reconcile（只读对账，开关 whale.crm.erp.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'crm.return.reconcile' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000025, 1, 'OA 采购申请推送 ERP 订单', 'OA', 'whale-oa', 'oa.purchase.push',
       '0 */10 * * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 2, 60, 1, 1,
       '迁移自 PurchaseOrderPushJob#push（按申请单号幂等，开关 whale.oa.purchase.push.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'oa.purchase.push' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000026, 1, 'OA 报销单推送 ERP 应付', 'OA', 'whale-oa', 'oa.expense.payable-push',
       '0 */10 * * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 2, 60, 1, 1,
       '迁移自 ExpensePayablePushJob#push（单条失败置 FAILED 继续后续，开关 whale.oa.expense.push.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'oa.expense.payable-push' AND deleted = 0);
