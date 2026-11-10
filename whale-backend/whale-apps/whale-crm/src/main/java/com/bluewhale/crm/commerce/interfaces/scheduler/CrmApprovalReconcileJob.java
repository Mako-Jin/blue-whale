package com.bluewhale.crm.commerce.interfaces.scheduler;

import com.bluewhale.common.jobschedule.ScheduledJob;
import com.bluewhale.crm.commerce.application.service.ApprovalReconcileAppService;
import com.bluewhale.crm.config.CrmOrderApprovalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审批结果对账定时任务。
 *
 * <p>whale-workflow 实例办结后没有主动回调实现，本任务定时拉取实例状态并对
 * 已办结实例触发幂等回调，兜住商务订单 / 报价 / 开票申请 / 退货申请四类单据的
 * 审批结果同步。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmApprovalReconcileJob {

    private final ApprovalReconcileAppService approvalReconcileAppService;

    private final CrmOrderApprovalProperties orderApprovalProperties;

    /**
     * 执行审批结果对账。
     *
     * @return 执行摘要（{@code skipped} 或本轮处理单据数）
     */
    @ScheduledJob(code = "crm.approval.reconcile",
            cron = "${whale.crm.order-approval.reconcile-cron:0 */2 * * * ?}",
            timeoutSeconds = 600)
    public String reconcile() {
        if (!orderApprovalProperties.isReconcileEnabled()) {
            return "skipped: approval reconcile disabled";
        }
        int reconciled = approvalReconcileAppService.reconcileAll();
        return "reconciled=" + reconciled;
    }
}
