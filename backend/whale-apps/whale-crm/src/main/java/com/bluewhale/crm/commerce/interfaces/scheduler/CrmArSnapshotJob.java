package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.crm.commerce.application.service.ArReconcileAppService;
import com.bluewhale.crm.commerce.application.view.ArSyncResultView;
import com.bluewhale.crm.config.CrmArProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 客户应收快照对账定时任务。
 *
 * <p>默认关闭；开启后按客户拉取 ERP 未结应收并保存当日账龄快照。</p>
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
     */
    @Scheduled(cron = "${whale.crm.ar.sync-cron:0 30 2 * * ?}")
    public void sync() {
        if (!arProperties.isEnabled()) {
            return;
        }
        ArSyncResultView result = arReconcileAppService.syncAll();
        log.info("应收对账完成：{}", result.getMessage());
    }
}
