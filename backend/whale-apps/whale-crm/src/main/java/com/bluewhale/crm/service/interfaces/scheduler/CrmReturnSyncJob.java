package com.bluewhale.crm.service.interfaces.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.crm.config.CrmErpProperties;
import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 退换货 ERP 同步定时任务：扫描 outbox 下发 + 按 CRM 单号对账。
 *
 * <p>由 whale-job 调度中心触发，返回值是执行摘要，直接落 {@code job_log.result}。
 * 下发按 outbox 幂等，对账只读；开关关闭时返回 {@code skipped} 而非失败。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmReturnSyncJob {

    private final ReturnRequestAppService returnRequestAppService;

    private final CrmErpProperties erpProperties;

    /**
     * 扫描 outbox 下发待执行退换货。
     *
     * @return 执行摘要（{@code skipped} 或下发成功条数）
     */
    @ScheduledJob(code = "crm.return.push",
            cron = "${whale.crm.erp.push-cron:0 */5 * * * ?}",
            timeoutSeconds = 1800)
    public String pushPending() {
        if (!erpProperties.isEnabled()) {
            return "skipped: crm erp disabled";
        }
        int pushed = returnRequestAppService.pushPending();
        if (pushed > 0) {
            log.info("退换货 ERP 下发完成，成功 {} 条", pushed);
        }
        return "pushed=" + pushed;
    }

    /**
     * 对账执行中 / 失败的退换货 ERP 状态。
     *
     * @return 执行摘要（{@code skipped} 或刷新条数）
     */
    @ScheduledJob(code = "crm.return.reconcile",
            cron = "${whale.crm.erp.reconcile-cron:0 0/30 * * * ?}",
            timeoutSeconds = 1800)
    public String reconcile() {
        if (!erpProperties.isEnabled()) {
            return "skipped: crm erp disabled";
        }
        int refreshed = returnRequestAppService.reconcileAll();
        if (refreshed > 0) {
            log.info("退换货 ERP 对账完成，刷新 {} 条", refreshed);
        }
        return "refreshed=" + refreshed;
    }
}
