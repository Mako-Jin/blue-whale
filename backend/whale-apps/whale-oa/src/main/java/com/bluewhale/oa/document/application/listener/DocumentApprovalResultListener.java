package com.bluewhale.oa.document.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.document.application.service.DocOutgoingAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 公文审批结果同步监听器。
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocumentApprovalResultListener {

    /** 业务类型：发文 */
    private static final String BIZ_TYPE_OUTGOING = "DOC_OUTGOING";

    private final DocOutgoingAppService outgoingAppService;

    /**
     * 处理审批结果同步事件。
     *
     * @param event 审批结果事件
     */
    @EventListener
    public void onApprovalResultSynced(ApprovalResultSyncedEvent event) {
        if (event == null || event.businessId() == null || event.businessType() == null) {
            return;
        }
        if (BIZ_TYPE_OUTGOING.equalsIgnoreCase(event.businessType().trim())) {
            outgoingAppService.syncApprovalResult(event.businessId(), event.result());
        }
    }
}
