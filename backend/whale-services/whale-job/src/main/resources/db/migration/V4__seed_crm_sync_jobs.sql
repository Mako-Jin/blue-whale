-- ---------------------------------------------------------------------
-- 种子数据：CRM 商务/服务/客户域远程任务（原进程内 @Scheduled 迁移至调度中心，
-- 2026-11-09 第十一批；接续 V2 的分析域 1001/1002）
--
-- whale-crm 的 /crm/internal/return-sync/**、/crm/internal/commerce-sync/**、
-- /crm/internal/customer-pool/** 仅内网开放（网关 internal-deny + 服务间内网
-- 令牌），经 httpInvokeJobHandler 由 Nacos 服务发现直连触发。各端点在对应
-- 集成开关（whale.crm.erp/ar/contract.enabled、whale.crm.pool-recycle-enabled）
-- 关闭时返回 code=0 data=-1 跳过，与原 @Scheduled 禁用语义一致，故任务默认
-- 启用不产生业务副作用；下发/对账均为幂等扫描，重复触发安全。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1003, 'CRM 退换货 ERP 下发', 'CRM', '0 */5 * * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/return-sync/push"}',
        'SMART', 1, '内置任务：每 5 分钟扫描 outbox 下发待执行退换货（ERP 集成关闭时跳过）');

INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1004, 'CRM 退换货 ERP 对账', 'CRM', '0 0/30 * * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/return-sync/reconcile"}',
        'SMART', 1, '内置任务：每 30 分钟对账执行中/失败的退换货 ERP 状态');

INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1005, 'CRM 商务订单 ERP 下发', 'CRM', '0 * * * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/commerce-sync/order/push"}',
        'SMART', 1, '内置任务：每分钟扫描 outbox 下发待推送商务订单（ERP 集成关闭时跳过）');

INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1006, 'CRM 商务订单 ERP 对账', 'CRM', '0 */10 * * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/commerce-sync/order/reconcile"}',
        'SMART', 1, '内置任务：每 10 分钟回读 ERP 履约状态');

INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1007, 'CRM 应收快照同步', 'CRM', '0 30 2 * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/commerce-sync/ar/sync"}',
        'SMART', 1, '内置任务：每日 02:30 按客户拉取 ERP 未结应收并保存当日账龄快照');

INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1008, 'CRM 合同投影对账', 'CRM', '0 0 3 * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/commerce-sync/contract/reconcile"}',
        'SMART', 1, '内置任务：每日 03:00 刷新已有台账客户的合同投影（单客户失败不中断）');

INSERT IGNORE INTO job_info (id, job_name, job_group, cron_expression, handler_bean, handler_param,
                      misfire_policy, status, remark)
VALUES (1009, 'CRM 公海自动回收', 'CRM', '0 0 2 * * ?', 'httpInvokeJobHandler',
        '{"service":"whale-crm","method":"POST","path":"/crm/internal/customer-pool/recycle"}',
        'SMART', 1, '内置任务：每日 02:00 按公海规则回收客户（pool-recycle-enabled 关闭时跳过）');
