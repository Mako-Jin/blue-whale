package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.crm.commerce.application.service.ArReconcileAppService;
import com.bluewhale.crm.commerce.application.view.ArSyncResultView;
import com.bluewhale.crm.config.CrmArProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 客户应收快照对账定时任务。
 *
 * <p>默认关闭；开启后按客户拉取 ERP 未结应收并保存当日账龄快照。
 * 关闭时返回 {@code skipped}，不向调度中心报失败。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmArSnapshotJob {

    private final ArReconcileAppService arReconcileAppService;

    private final CrmArProperties arProperties;

    /**
     * 同步当日应收快照。
     *
     * @return 执行摘要（{@code skipped} 或同步/跳过/失败客户数）
     */
    @ScheduledJob(code = "crm.ar.snapshot",
            cron = "${whale.crm.ar.sync-cron:0 30 2 * * ?}",
            timeoutSeconds = 1800)
    public String sync() {
        if (!arProperties.isEnabled()) {
            return "skipped: ar sync disabled";
        }
        ArSyncResultView result = arReconcileAppService.syncAll();
        log.info("应收对账完成：{}", result.getMessage());
        return "synced=" + result.getSynced() + ", skipped=" + result.getSkipped()
                + ", failed=" + result.getFailed();
    }
}
