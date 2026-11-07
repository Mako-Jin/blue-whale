package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.crm.commerce.application.service.ContractAppService;
import com.bluewhale.crm.config.CrmContractProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 合同投影定时对账任务。
 *
 * <p>仅在 {@code whale.crm.contract.enabled=true} 时执行；单个客户失败只记录日志，
 * 不中断本轮对账，也不改动本地已有台账。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmContractSyncJob {

    private final ContractAppService contractAppService;

    private final CrmContractProperties contractProperties;

    /**
     * 定时刷新已有台账客户的合同投影。
     */
    @Scheduled(cron = "${whale.crm.contract.sync-cron:0 0 3 * * ?}")
    public void reconcile() {
        if (!contractProperties.isEnabled()) {
            return;
        }
        try {
            int synced = contractAppService.reconcile();
            if (synced > 0) {
                log.info("合同投影定时对账完成，刷新 {} 条", synced);
            }
        } catch (Exception exception) {
            log.warn("合同投影定时对账失败", exception);
        }
    }
}
