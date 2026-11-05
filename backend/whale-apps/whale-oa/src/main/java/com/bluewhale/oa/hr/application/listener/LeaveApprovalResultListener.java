package com.bluewhale.oa.hr.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.hr.application.service.LeaveAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 审批结果同步监听器：审批终态回写请假申请。
 *
 * <p>仅处理 {@code businessType = LEAVE} 的事件，其余业务忽略；
 * 请假服务自身对重复终态幂等，避免审批重试造成额度二次扣减。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class LeaveApprovalResultListener {

    /** 请假业务类型 */
    private static final String BIZ_TYPE_LEAVE = "LEAVE";

    private final LeaveAppService leaveAppService;

    /**
     * 处理审批结果同步事件。
     *
     * @param event 审批结果事件
     */
    @EventListener
    public void onApprovalResultSynced(ApprovalResultSyncedEvent event) {
        if (event == null || !BIZ_TYPE_LEAVE.equalsIgnoreCase(event.businessType())
                || event.businessId() == null) {
            return;
        }
        leaveAppService.syncApprovalResult(event.businessId(), event.result());
    }
}
