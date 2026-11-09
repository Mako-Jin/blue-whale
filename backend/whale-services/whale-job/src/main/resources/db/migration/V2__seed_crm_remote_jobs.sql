-- ---------------------------------------------------------------------
-- 种子数据：CRM 分析类远程任务（原进程内 @Scheduled 迁移至调度中心，2026-11-09 第八批）
--
-- whale-crm 的 /crm/internal/analytics/** 仅内网开放（网关 internal-deny +
-- 服务间内网令牌），经 httpInvokeJobHandler 由 Nacos 服务发现直连触发；
-- 快照按 metricCode+statDate+dimensionType+dimensionId 幂等覆盖，行动计划
-- 为规则化生成并自动关闭已解除的未关闭行动项，均不依赖调用时刻。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1001, 'CRM 分析快照生成', 'CRM', '0 0 2 * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/analytics/snapshot"}',
        'SMART', 1, '内置任务：每日 02:00 经内网触发 whale-crm 生成昨日指标快照（幂等覆盖）');

INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1002, 'CRM 行动计划生成', 'CRM', '0 30 2 * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/analytics/action-plan"}',
        'SMART', 1, '内置任务：每日 02:30 经内网触发 whale-crm 生成行动计划');
