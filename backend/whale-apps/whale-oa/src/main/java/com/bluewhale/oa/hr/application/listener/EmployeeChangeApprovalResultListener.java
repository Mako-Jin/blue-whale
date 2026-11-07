package com.bluewhale.oa.hr.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.hr.application.service.EmployeeChangeAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 人员异动审批结果同步监听器。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class EmployeeChangeApprovalResultListener {

    /** 业务类型：人员异动 */
    private static final String BIZ_TYPE_CHANGE = "HR_CHANGE";

    private final EmployeeChangeAppService employeeChangeAppService;

    /**
     * 处理审批结果同步事件（幂等）。
     *
     * @param event 审批结果事件
     */
    @EventListener
    public void onApprovalResultSynced(ApprovalResultSyncedEvent event) {
        if (event == null || event.businessId() == null || event.businessType() == null) {
            return;
        }
        if (BIZ_TYPE_CHANGE.equalsIgnoreCase(event.businessType().trim())) {
            employeeChangeAppService.syncApprovalResult(event.businessId(), event.result());
        }
    }
}
