package com.bluewhale.oa.expense.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.expense.application.service.ExpenseAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 审批结果同步监听器：审批终态回写报销单。
 *
 * <p>仅处理 {@code businessType = EXPENSE} 的事件；报销服务对重复终态幂等，
 * 保证 Outbox 审批通过事件只追加一次。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ExpenseApprovalResultListener {

    /** 报销业务类型 */
    private static final String BIZ_TYPE_EXPENSE = "EXPENSE";

    private final ExpenseAppService expenseAppService;

    /**
     * 处理审批结果同步事件。
     *
     * @param event 审批结果事件
     */
    @EventListener
    public void onApprovalResultSynced(ApprovalResultSyncedEvent event) {
        if (event == null || !BIZ_TYPE_EXPENSE.equalsIgnoreCase(event.businessType())
                || event.businessId() == null) {
            return;
        }
        expenseAppService.syncApprovalResult(event.businessId(), event.result());
    }
}
