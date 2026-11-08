-- =====================================================================
-- 已迁移业务任务的 job_info 种子（幂等）——第六批：LMS 催办 + Party 通知扫描
-- =====================================================================
-- 幂等：按 job_code 判重（job_info 无 job_code 唯一键，老库中可能已有手工
--       插入的同名任务），重复执行不会重复产生任务。
-- 说明：两个任务都是「按日幂等通知」类，重复触发只会让当日已发场景返回 0，
--       因此 misfire 用 FIRE_ONCE（错过当日应补跑一次），失败可重试。
-- =====================================================================

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000019, 1, 'LMS 未完成必修催办', 'LMS', 'whale-lms', 'lms.urge.daily',
       '0 0 9 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'FIRE_ONCE', 1, 300, 1, 1,
       '迁移自 LmsUrgeJob#urgeDaily（按日幂等，重复触发不重复发；cron 可配 whale.lms.urge.cron）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'lms.urge.daily' AND deleted = 0);

INSERT INTO job_info (id, tenant_id, job_name, job_group, executor_app, job_code,
                      cron_expression, handler_bean, handler_param,
                      misfire_policy, max_retry, retry_interval_seconds,
                      shard_total, status, remark)
SELECT 9100000000000000020, 1, 'Party 党务/工会通知扫描', 'PARTY', 'whale-party', 'party.notice.scan',
       '0 0 9 * * *', 'httpJobHandler', '{"timeoutMillis":1800000}',
       'FIRE_ONCE', 1, 300, 1, 1,
       '迁移自 PartyNoticeJob#scan（按场景去重，重复扫描当日回 0；cron 可配 whale.party.notice-cron）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE job_code = 'party.notice.scan' AND deleted = 0);
