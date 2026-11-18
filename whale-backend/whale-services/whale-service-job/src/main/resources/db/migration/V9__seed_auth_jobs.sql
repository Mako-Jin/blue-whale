-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）——第七批：Auth 口令到期提醒
-- =====================================================================
-- 幂等：按 job_code 判重（job_info 无 job_code 唯一键，老库中可能已有手工
--       插入的同名任务），重复执行不会重复产生任务。
-- 说明：该任务按日幂等（notifyIfDue 保证同一账号同一到期窗口只提醒一次），
--       重复触发不会重复发通知，因此 misfire 用 FIRE_ONCE 补跑一次当日。
-- =====================================================================

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000027, 1, 'Auth 口令到期提醒', 'AUTH', 'whale-auth', 'auth.password.expiry-notice',
       '0 0 2 * * ?', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'FIRE_ONCE', 1, 300, 1, 1,
       '迁移自 PasswordExpiryNoticeJob#notifyExpiringPasswords（按日幂等，重复触发不重复提醒；cron 可配 whale.auth.password.notice-cron）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'auth.password.expiry-notice' AND deleted = 0);
