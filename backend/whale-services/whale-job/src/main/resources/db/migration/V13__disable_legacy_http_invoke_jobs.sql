-- =====================================================================
-- 停用旧的 httpInvokeJobHandler 直连任务行（幂等）
-- =====================================================================
-- 背景：CRM 商务/服务/客户/分析四域的任务有过两代迁移形态：
--   1) 旧通道：job_info(1001–1009) + handler_bean='httpInvokeJobHandler'
--      + handler_param 里的 service/path，直连 /crm/internal/** 控制器；
--   2) 新通道：job_info(job_code=crm.*) + handler_bean='httpJobHandler'
--      + executor_app，由执行器注册表寻址后调 POST /internal/job/{code}/run，
--      业务侧方法带 @ScheduledJob 注解、开关在方法内判（关闭返回 skipped）。
--
-- 2026-11-09 双线合并时删除了旧通道的三份种子脚本（重复版本号），但
-- **已应用过这些脚本的环境，库里 1001–1009 行仍然存在**：删除脚本不会删行。
-- 那些环境因此会同时存在两套行，同一批任务被两套机制各调度一次（推单/对账
-- 各跑两遍，outbox 幂等救不了重复的对账扫描与通知）。
--
-- 本迁移只停用「已有等价 job_code 通道」的旧行，且仅当等价行处于启用状态；
-- 找不到替代的旧行保持原状（不制造功能缺口），由人工判断。
--
-- 运维提示：删除脚本会让已应用环境在 Flyway 校验期报
-- 「Detected applied migration not resolved locally: 2/3/4」，
-- 需在该环境执行一次 `flyway repair`（或临时关掉
-- WHALE_FLYWAY_VALIDATE_ON_MIGRATE）后再启动，否则 bw-job 起不来。
-- =====================================================================

UPDATE job_info AS j
SET j.status = 0,
    j.update_time = NOW(),
    j.remark = LEFT(CONCAT(COALESCE(j.remark, ''),
        '｜2026-11-09 合并收口：旧 httpInvokeJobHandler 通道已由 job_code 执行器通道取代，自动停用'), 255)
WHERE j.status = 1
  AND j.deleted = 0
  AND j.handler_bean = 'httpInvokeJobHandler'
  AND EXISTS (
      SELECT 1
      FROM (SELECT job_code FROM job_info WHERE deleted = 0 AND status = 1 AND job_code IS NOT NULL) AS k
      WHERE k.job_code = CASE
          WHEN j.handler_param LIKE '%/crm/internal/return-sync/push%'         THEN 'crm.return.push'
          WHEN j.handler_param LIKE '%/crm/internal/return-sync/reconcile%'    THEN 'crm.return.reconcile'
          WHEN j.handler_param LIKE '%/crm/internal/commerce-sync/order/push%' THEN 'crm.order.push'
          WHEN j.handler_param LIKE '%/crm/internal/commerce-sync/order/reconcile%' THEN 'crm.order.reconcile'
          WHEN j.handler_param LIKE '%/crm/internal/commerce-sync/ar/sync%'    THEN 'crm.ar.snapshot'
          WHEN j.handler_param LIKE '%/crm/internal/commerce-sync/contract/reconcile%' THEN 'crm.contract.sync'
          WHEN j.handler_param LIKE '%/crm/internal/customer-pool/recycle%'    THEN 'crm.pool.recycle'
          WHEN j.handler_param LIKE '%/crm/internal/analytics/snapshot%'       THEN 'crm.analytics.snapshot'
          WHEN j.handler_param LIKE '%/crm/internal/analytics/action-plan%'    THEN 'crm.analytics.action-plan'
          ELSE NULL
      END
  );
