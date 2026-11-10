package com.bluewhale.oa.hr.application.listener;

import com.bluewhale.oa.approval.application.event.ApprovalResultSyncedEvent;
import com.bluewhale.oa.hr.application.service.TripAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 审批结果同步监听器：审批终态回写出差申请。
 *
 * <p>仅处理 {@code businessType = TRIP} 的事件；出差服务对重复终态幂等，
 * 保证预支记录只生成一次。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class TripApprovalResultListener {

    /** 出差业务类型 */
    private static final String BIZ_TYPE_TRIP = "TRIP";

    private final TripAppService tripAppService;

    /**
     * 处理审批结果同步事件。
     *
     * @param event 审批结果事件
     */
    @EventListener
    public void onApprovalResultSynced(ApprovalResultSyncedEvent event) {
        if (event == null || !BIZ_TYPE_TRIP.equalsIgnoreCase(event.businessType())
                || event.businessId() == null) {
            return;
        }
        tripAppService.syncApprovalResult(event.businessId(), event.result());
    }
}
