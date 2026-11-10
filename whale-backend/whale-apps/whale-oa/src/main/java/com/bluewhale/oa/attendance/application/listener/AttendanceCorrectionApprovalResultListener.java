package com.bluewhale.oa.attendance.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.attendance.application.service.AttendanceCorrectionAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 审批结果同步监听器：审批终态回写补卡申请。
 *
 * <p>仅处理 {@code businessType = ATT_CORRECTION} 的事件；补卡服务对重复终态幂等，
 * 保证补卡记录只写入一次。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class AttendanceCorrectionApprovalResultListener {

    /** 补卡业务类型 */
    private static final String BIZ_TYPE = "ATT_CORRECTION";

    private final AttendanceCorrectionAppService correctionAppService;

    /**
     * 处理审批结果同步事件。
     *
     * @param event 审批结果事件
     */
    @EventListener
    public void onApprovalResultSynced(ApprovalResultSyncedEvent event) {
        if (event == null || !BIZ_TYPE.equalsIgnoreCase(event.businessType())
                || event.businessId() == null) {
            return;
        }
        correctionAppService.syncApprovalResult(event.businessId(), event.result());
    }
}
