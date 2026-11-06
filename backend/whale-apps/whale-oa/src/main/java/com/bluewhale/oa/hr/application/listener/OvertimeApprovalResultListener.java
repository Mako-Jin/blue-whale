package com.bluewhale.oa.hr.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.hr.application.service.OvertimeAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 审批结果同步监听器：审批终态回写加班申请。
 *
 * <p>仅处理 {@code businessType = OVERTIME} 的事件；加班服务对重复终态幂等，
 * 保证调休额度只入账一次。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class OvertimeApprovalResultListener {

    /** 加班业务类型 */
    private static final String BIZ_TYPE_OVERTIME = "OVERTIME";

    private final OvertimeAppService overtimeAppService;

    /**
     * 处理审批结果同步事件。
     *
     * @param event 审批结果事件
     */
    @EventListener
    public void onApprovalResultSynced(ApprovalResultSyncedEvent event) {
        if (event == null || !BIZ_TYPE_OVERTIME.equalsIgnoreCase(event.businessType())
                || event.businessId() == null) {
            return;
        }
        overtimeAppService.syncApprovalResult(event.businessId(), event.result());
    }
}
