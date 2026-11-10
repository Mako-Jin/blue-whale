package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.crm.commerce.application.service.SalesOrderAppService;
import com.bluewhale.crm.config.CrmErpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 商务订单下发与对账定时任务。
 *
 * <p>ERP 下发默认关闭；开启后按 outbox 重试退避下发，并定时回读 ERP 履约状态。
 * 由 whale-job 调度中心触发，返回值是执行摘要，直接落 {@code job_log.result}。</p>
 *
 * <p>下发按 outbox 幂等（重复下发在 ERP 侧按外部单号去重），对账只读，两者都允许重试；
 * 开关关闭时返回 {@code skipped} 而非失败，便于区分「配置没开」与「推送出错」。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmOrderSyncJob {

    private final SalesOrderAppService salesOrderAppService;

    private final CrmErpProperties erpProperties;

    /**
     * 扫描 outbox 下发待推送订单。
     *
     * @return 执行摘要（{@code skipped} 或下发成功单数）
     */
    @ScheduledJob(code = "crm.order.push",
            cron = "${whale.crm.erp.push-cron:0 * * * * ?}",
            timeoutSeconds = 1800)
    public String pushPending() {
        if (!erpProperties.isEnabled()) {
            return "skipped: crm erp disabled";
        }
        int pushed = salesOrderAppService.pushPending();
        if (pushed > 0) {
            log.info("商务订单下发完成，成功 {} 单", pushed);
        }
        return "pushed=" + pushed;
    }

    /**
     * 对账 ERP 履约状态。
     *
     * @return 执行摘要（{@code skipped} 或刷新单数）
     */
    @ScheduledJob(code = "crm.order.reconcile",
            cron = "${whale.crm.erp.reconcile-cron:0 */10 * * * ?}",
            timeoutSeconds = 1800)
    public String reconcile() {
        if (!erpProperties.isEnabled()) {
            return "skipped: crm erp disabled";
        }
        int refreshed = salesOrderAppService.reconcile();
        if (refreshed > 0) {
            log.info("商务订单对账完成，刷新 {} 单", refreshed);
        }
        return "refreshed=" + refreshed;
    }
}
