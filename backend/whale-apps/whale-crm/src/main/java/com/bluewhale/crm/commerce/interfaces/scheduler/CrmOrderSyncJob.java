package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.crm.commerce.application.service.SalesOrderAppService;
import com.bluewhale.crm.config.CrmErpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 商务订单下发与对账定时任务。
 *
 * <p>ERP 下发默认关闭；开启后按 outbox 重试退避下发，并定时回读 ERP 履约状态。</p>
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
     */
    @Scheduled(cron = "${whale.crm.erp.push-cron:0 * * * * ?}")
    public void pushPending() {
        if (!erpProperties.isEnabled()) {
            return;
        }
        int pushed = salesOrderAppService.pushPending();
        if (pushed > 0) {
            log.info("商务订单下发完成，成功 {} 单", pushed);
        }
    }

    /**
     * 对账 ERP 履约状态。
     */
    @Scheduled(cron = "${whale.crm.erp.reconcile-cron:0 */10 * * * ?}")
    public void reconcile() {
        if (!erpProperties.isEnabled()) {
            return;
        }
        int refreshed = salesOrderAppService.reconcile();
        if (refreshed > 0) {
            log.info("商务订单对账完成，刷新 {} 单", refreshed);
        }
    }
}
