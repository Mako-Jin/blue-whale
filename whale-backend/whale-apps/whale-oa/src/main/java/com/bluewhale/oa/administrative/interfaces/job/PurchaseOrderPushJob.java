package com.bluewhale.oa.administrative.interfaces.job;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.oa.administrative.application.service.PurchaseAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 采购申请 ERP 采购订单推送任务。
 *
 * <p>由 whale-job 调度中心触发，返回值是执行摘要，直接落 {@code job_log.result}。
 * 推送按申请单号幂等（ERP 侧按外部单号去重），失败可重试；开关关闭时返回
 * {@code skipped} 而非失败。</p>
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
     *
     * @return 执行摘要（{@code skipped} 或推送成功条数）
     */
    @ScheduledJob(code = "oa.purchase.push",
            cron = "${whale.oa.purchase.push.cron:0 */10 * * * *}",
            timeoutSeconds = 1800)
    public String push() {
        if (!enabled) {
            return "skipped: purchase push disabled";
        }
        int success = purchaseAppService.pushPending();
        if (success > 0) {
            log.info("采购申请 ERP 订单推送任务完成: success={}", success);
        }
        return "pushed=" + success;
    }
}
