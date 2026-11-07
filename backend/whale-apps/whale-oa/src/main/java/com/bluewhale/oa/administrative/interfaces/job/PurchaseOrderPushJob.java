package com.bluewhale.oa.administrative.interfaces.job;

import com.bluewhale.oa.administrative.application.service.PurchaseAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 采购申请 ERP 采购订单推送任务。
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderPushJob {

    private final PurchaseAppService purchaseAppService;

    /** 推送开关 */
    @Value("${whale.oa.purchase.push.enabled:true}")
    private boolean enabled;

    /**
     * 扫描并推送待推送的采购申请。
     */
    @Scheduled(cron = "${whale.oa.purchase.push.cron:0 */10 * * * *}")
    public void push() {
        if (!enabled) {
            return;
        }
        int success = purchaseAppService.pushPending();
        if (success > 0) {
            log.info("采购申请 ERP 订单推送任务完成: success={}", success);
        }
    }
}
