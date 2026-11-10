package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.crm.commerce.application.service.ContractAppService;
import com.bluewhale.crm.config.CrmContractProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 合同投影定时对账任务。
 *
 * <p>仅在 {@code whale.crm.contract.enabled=true} 时执行；单个客户失败由服务层记录日志并继续，
 * 不中断本轮对账，也不改动本地已有台账。整轮失败不再吞异常——否则调度中心会把
 * 「对账长期停摆」记成成功。</p>
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
     *
     * @return 执行摘要（{@code skipped} 或刷新条数）
     */
    @ScheduledJob(code = "crm.contract.sync",
            cron = "${whale.crm.contract.sync-cron:0 0 3 * * ?}",
            timeoutSeconds = 1800)
    public String reconcile() {
        if (!contractProperties.isEnabled()) {
            return "skipped: contract sync disabled";
        }
        int synced = contractAppService.reconcile();
        log.info("合同投影定时对账完成，刷新 {} 条", synced);
        return "synced=" + synced;
    }
}
