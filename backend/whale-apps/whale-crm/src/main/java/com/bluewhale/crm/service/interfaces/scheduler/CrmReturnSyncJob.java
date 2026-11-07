package com.bluewhale.crm.service.interfaces.scheduler;

import com.bluewhale.crm.service.application.service.ReturnRequestAppService;
import com.bluewhale.crm.config.CrmErpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 退换货 ERP 同步定时任务：扫描 outbox 下发 + 按 CRM 单号对账。
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
     */
    @Scheduled(cron = "${whale.crm.erp.push-cron:0 */5 * * * ?}")
    public void pushPending() {
        if (!erpProperties.isEnabled()) {
            return;
        }
        int pushed = returnRequestAppService.pushPending();
        if (pushed > 0) {
            log.info("退换货 ERP 下发完成，成功 {} 条", pushed);
        }
    }

    /**
     * 对账执行中 / 失败的退换货 ERP 状态。
     */
    @Scheduled(cron = "${whale.crm.erp.reconcile-cron:0 0/30 * * * ?}")
    public void reconcile() {
        if (!erpProperties.isEnabled()) {
            return;
        }
        int refreshed = returnRequestAppService.reconcileAll();
        if (refreshed > 0) {
            log.info("退换货 ERP 对账完成，刷新 {} 条", refreshed);
        }
    }
}
