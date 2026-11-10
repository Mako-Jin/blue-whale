-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）——第五批：OA 提醒/对账 3 个
-- =====================================================================
-- 幂等：按 job_code 判重（job_info 无 job_code 唯一键，老库中可能已有手工
--       插入的同名任务），重复执行不会重复产生任务。
-- 说明：三个任务均为「通知/回写」类，各自带业务开关，关闭时返回 skipped 而非失败，
--       因此种子一律启用、由配置决定是否真跑（推送 ERP 的两个任务仍留在最后一批）。
-- =====================================================================

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000016, 1, 'OA 审批自动催办', 'OA', 'whale-oa', 'oa.approval.auto-urge',
       '0 0 */2 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 ApprovalMaintenanceJob#autoUrge（消息服务按天去重，开关 auto-urge.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'oa.approval.auto-urge' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000017, 1, 'OA 审批状态对账', 'OA', 'whale-oa', 'oa.approval.reconcile',
       '0 */30 * * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'DO_NOTHING', 2, 300, 1, 1,
       '迁移自 ApprovalMaintenanceJob#reconcile（回写幂等，失败可重试；开关 reconcile.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'oa.approval.reconcile' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000018, 1, 'OA 出差报告未交提醒', 'OA', 'whale-oa', 'oa.trip.report-reminder',
       '0 0 9 * * *', 'httpJobHandler', '{"timeoutMillis":900000}',
       'DO_NOTHING', 1, 300, 1, 1,
       '迁移自 TripReportReminderJob#remind（开关 report-reminder.enabled）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'oa.trip.report-reminder' AND deleted = 0);
